/* ============================================================
   OFFICE DREAMS — interactive tutorial (coach marks).
   A glowing ring points at the button to press, a bubble explains
   why. Steps finish by themselves when the player does the thing.
   ============================================================ */
const gwOn = () => !!(NET.eco && NET.eco.giveaway && !NET.eco.giveaway.done);
const TUT = [
  { k: 'tut.welcome', tab: ['studio', 'games'], target: null, manual: true, done: () => UI.genrePicked || S.stats.started >= 1 }, // skipped as soon as the player just starts playing
  { k: 'tut.genre', tab: ['studio', 'games'], target: '.genres', done: () => UI.genrePicked || S.stats.started >= 1 },
  { k: 'tut.start', tab: ['studio', 'games'], target: '[data-act="start"]', done: () => S.stats.started >= 1 },
  { k: 'tut.tap', tab: ['office'], target: '#workbtn', done: () => S.stats.taps >= 15 },
  { k: 'tut.energy', tab: ['office'], target: '.energy', manual: true },
  { k: 'tut.release', tab: ['office'], target: '.pmini', manual: true },
  { k: 'tut.cash', tab: ['office'], target: '.res .cash', manual: true },
  { k: 'tut.gems', tab: ['office'], target: '.res .gem', manual: true },
  { k: 'tut.hire', tab: ['office'], target: '.side [data-act="go:studio:team"]', done: () => hiredCount(S) >= 1, skippable: true },
  { k: 'tut.mood', tab: ['office'], target: '.opm.mood', manual: true },
  { k: 'tut.life', tab: ['office'], target: '#scene', manual: true },
  { k: 'tut.quests', tab: null, target: '#tabs [data-act="tab:quests"]', manual: true },
  { k: 'tut.empire', tab: ['empire'], target: '.floor.next, #tower', manual: true },
  { k: 'tut.city', tab: ['office'], target: '.citybn', manual: true, done: () => !!lsGet('od2_tip_city') },
  { k: 'tut.pvp', tab: ['office'], target: '.side [data-act="pvp"]', manual: true, done: () => !!lsGet('od2_tip_pvp') },
  { k: 'tut.studio', tab: ['office'], target: '.op-studio', done: () => !!studioName(), skippable: true },
  { k: 'tut.airdrop', tab: ['drop', 'home'], target: null, manual: true },
  { k: 'tut.points', tab: ['drop', 'home'], target: '.ptsp', manual: true },
  { k: 'tut.giveaway', tab: ['drop', 'home'], target: '#gwcard, .gwnone', manual: true, done: () => !!lsGet('od2_tip_gw') },
  { k: 'tut.token', tab: ['drop', 'token'], target: '.adrop, .tokhero2', manual: true },
  { k: 'tut.wallet', tab: ['drop', 'token'], target: null, manual: true, done: () => !!(NET.eco && NET.eco.sol) },
  { k: 'tut.friends', tab: ['drop', 'friends'], target: '.invite', manual: true },
  { k: 'tut.help', tab: ['office'], target: '.helpbtn', manual: true },
  { k: 'tut.done', tab: ['office'], target: null, manual: true },
];
const COACH = { i: -1, el: null, scrolled: -1 };
// one-off "new feature" hints for players who already finished the tutorial
const TIPS = [
  { id: 'pvp', k: 'tut.pvp_new', tab: ['office'], target: '.side [data-act="pvp"]', act: 'pvp', when: () => S.stats.taps >= 15 },
  { id: 'studio', k: 'tut.studio_new', tab: ['office'], target: '.op-studio', act: 'studio', when: () => S.stats.taps >= 15 && !studioName() },
  { id: 'city', k: 'tut.city_new', tab: ['office'], target: '.citybn', act: 'cityopen', when: () => S.stats.taps >= 15 },
  { id: 'secrets', k: 'tut.secrets_new', tab: ['city'], target: '.ct-scene', act: '', when: () => true },
  { id: 'gw', k: 'tut.gw_new', tab: ['drop', 'home'], target: '#gwcard', act: '', when: () => gwOn() },
  { id: 'reno', k: 'tut.reno_new', tab: ['empire'], target: '.floor .reno', act: '', when: () => renoStars(S) === 0 },
];
const OLD_TUT_DONE = 11; // the tutorial had 11 steps before the battle step was added: 11+ means "finished"

function tutLoad() {
  const v = lsGet('od2_tut');
  // 'v2:<step>' since the full tutorial; plain numbers come from older versions with fewer steps
  const cur = v && v.slice(0, 3) === 'v2:' ? Number(v.slice(3)) : null;
  COACH.i = v === null ? -1 : v === 'done' ? TUT.length : cur !== null ? Math.min(cur, TUT.length) : Number(v) >= OLD_TUT_DONE ? TUT.length : 0;
}
function tipActive() {
  if (COACH.i < TUT.length || UI.pvp) return null;
  // no new tips while the player is tapping WORK: a tip would scroll the page under their finger
  if (Date.now() - (UI.tapAt || 0) < 4000) return null;
  return TIPS.find((t) => !lsGet('od2_tip_' + t.id) && t.when()) || null;
}
function tipDone(id) { lsSet('od2_tip_' + id, '1'); }
function tutSave() { lsSet('od2_tut', COACH.i >= TUT.length ? 'done' : 'v2:' + COACH.i); }
function tutStart() {
  COACH.i = 0;
  // steps the player has already done are skipped right away, so the counter starts at 1
  while (tutActive() && TUT[COACH.i].done && TUT[COACH.i].done()) COACH.i++;
  lsSet('od2_tut_from', String(COACH.i));
  lsSet('od2_tut_n', '1'); lsSet('od2_tut_tot', '');
  tutSave();
  if (!tutActive()) { tutStop(); return; }
  tutGoto();
}
// numbering over the steps this player actually sees: "1/10", not "4/13" when the first steps were already done
function tutStepText() {
  const n = Math.max(1, Number(lsGet('od2_tut_n')) || 1);
  const ahead = TUT.slice(COACH.i + 1).filter((s) => !(s.done && s.done())).length; // later steps already done are skipped
  // the total can only shrink (a later step gets done by itself), never grow
  const total = Math.min(n + ahead, Math.max(n, Number(lsGet('od2_tut_tot')) || 99));
  lsSet('od2_tut_tot', String(total));
  return n + '/' + total;
}
function tutStop() {
  COACH.i = TUT.length;
  tutSave();
  if (COACH.el) COACH.el.hidden = true;
}
function tutActive() { return COACH.i >= 0 && COACH.i < TUT.length; }
function tutGoto() {
  const st = TUT[COACH.i];
  if (!st) return;
  COACH.scrolled = -1;
  if (st.tab) go(st.tab[0], st.tab[1]);
}
function tutNext() {
  if (TUT[COACH.i] && TUT[COACH.i].k === 'tut.pvp') tipDone('pvp'); // seen in the tutorial: no "new" hint later
  if (TUT[COACH.i] && TUT[COACH.i].k === 'tut.studio') tipDone('studio');
  if (TUT[COACH.i] && TUT[COACH.i].k === 'tut.city') tipDone('city');
  if (TUT[COACH.i] && TUT[COACH.i].k === 'tut.giveaway') tipDone('gw');
  COACH.i++;
  tutSave();
  if (COACH.i >= TUT.length) { tutStop(); confetti(60); return; }
  // skip steps that are already done (e.g. the player hired before we asked)
  while (tutActive() && TUT[COACH.i].done && TUT[COACH.i].done()) { COACH.i++; tutSave(); }
  if (!tutActive()) { tutStop(); return; }
  lsSet('od2_tut_n', String((Number(lsGet('od2_tut_n')) || 1) + 1));
  tutGoto();
}
function tutEl() {
  if (COACH.el) return COACH.el;
  const el = document.createElement('div');
  el.id = 'coach';
  el.innerHTML = '<div class="c-ring"></div><div class="c-bub" role="dialog" aria-live="polite"><div class="c-step px"></div><p class="c-text"></p><div class="c-btns"></div></div>';
  document.body.appendChild(el);
  COACH.el = el;
  return el;
}
// called a few times per second from the main loop
function tutUpdate() {
  if (!S) return;
  const tip = tutActive() ? null : tipActive();
  if (!tutActive() && !tip) { if (COACH.el) COACH.el.hidden = true; return; }
  const st = tip || TUT[COACH.i];
  if (!tip && st.done && st.done()) { tutNext(); return; }
  const el = tutEl();
  // never cover a modal (release, event…) — wait for it to close
  if (UI.modal) { el.hidden = true; return; }
  el.hidden = false;
  const ring = el.querySelector('.c-ring'), bub = el.querySelector('.c-bub');
  const onTab = !st.tab || (UI.tab === st.tab[0] && (!st.tab[1] || UI.sub[st.tab[0]] === st.tab[1]));
  const target = st.target && onTab ? document.querySelector(st.target) : null;
  if (tip && !target) { el.hidden = true; return; } // a hint waits for its screen, it never drags the player around
  // text + buttons (re-rendered only when the step or language changes)
  const sig = (tip ? 'tip' + tip.id : COACH.i) + LANG + (target ? 1 : 0);
  if (bub.dataset.sig !== sig && tip) {
    bub.dataset.sig = sig;
    el.querySelector('.c-step').textContent = L('tut.new');
    el.querySelector('.c-text').textContent = L(st.k);
    el.querySelector('.c-btns').innerHTML = tip.act
      ? `<button class="btn sm glow" data-act="tip:go:${tip.id}">${L('tut.try')}</button><button class="btn sm ghost" data-act="tip:later:${tip.id}">${L('tut.later')}</button>`
      : `<button class="btn sm glow" data-act="tip:later:${tip.id}">${L('tut.got')}</button>`;
  }
  if (bub.dataset.sig !== sig) {
    bub.dataset.sig = sig;
    el.querySelector('.c-step').textContent = tutStepText();
    el.querySelector('.c-text').textContent = L(st.k);
    const btns = [];
    if (st.target && !target && st.tab) btns.push(`<button class="btn sm" data-act="tut:go">${L('tut.show')}</button>`);
    if (st.manual) btns.push(`<button class="btn sm glow" data-act="tut:next">${L(COACH.i === TUT.length - 1 ? 'tut.finish' : 'tut.next')}</button>`);
    if (st.skippable) btns.push(`<button class="btn sm ghost" data-act="tut:next">${L('tut.later')}</button>`);
    // on the first screen "skip" is a real button: easy to find for anyone who already knows the game
    btns.push(COACH.i === 0 ? `<button class="btn sm ghost skip" data-act="tut:skip">${L('tut.skip')}</button>` : `<button class="linkbtn" data-act="tut:skip">${L('tut.skip')}</button>`);
    el.querySelector('.c-btns').innerHTML = btns.join('');
  }
  if (!target) {
    ring.hidden = true;
    bub.className = 'c-bub center';
    return;
  }
  let r = target.getBoundingClientRect();
  if ((r.top < 70 || r.bottom > window.innerHeight - 80) && COACH.scrolled !== COACH.i) {
    COACH.scrolled = COACH.i;
    target.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
  r = target.getBoundingClientRect();
  ring.hidden = false;
  const pad = 6;
  ring.style.left = (r.left - pad) + 'px';
  ring.style.top = (r.top - pad) + 'px';
  ring.style.width = (r.width + pad * 2) + 'px';
  ring.style.height = (r.height + pad * 2) + 'px';
  // the office action row sits right above the WORK button: its tips open upwards so they never cover it
  const below = !target.closest('.side') && r.top + r.height / 2 < window.innerHeight * 0.5;
  bub.className = 'c-bub ' + (below ? 'below' : 'above');
  const bw = Math.min(320, window.innerWidth - 24);
  const cx = Math.max(12, Math.min(window.innerWidth - bw - 12, r.left + r.width / 2 - bw / 2));
  bub.style.left = cx + 'px';
  bub.style.width = bw + 'px';
  if (below) { bub.style.top = (r.bottom + pad + 14) + 'px'; bub.style.bottom = 'auto'; }
  else { bub.style.bottom = (window.innerHeight - r.top + pad + 14) + 'px'; bub.style.top = 'auto'; }
  bub.style.setProperty('--ax', Math.max(18, Math.min(bw - 18, r.left + r.width / 2 - cx)) + 'px');
}
