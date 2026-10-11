/* ============================================================
   OFFICE DREAMS — UI main: render loop, actions, modals, boot
   ============================================================ */
const BUILD = { mode: 'auto', version: '1.0.0' }; // build.js replaces mode for the demo artifact

/* ---------- rendering ---------- */
function affordSig() {
  const c = S.cash;
  let s = '';
  for (const r of ROLES) {
    const st = S.staff[r];
    s += st.lvl > 0 ? (st.lvl < MAX_LVL && c >= upCost(r, st.lvl) ? 1 : 0) : (c >= ROLE[r].hire ? 1 : 0);
    s += st.lvl > 0 ? moodClass(st.mood).charAt(0) : '';
  }
  s += c >= youUpCost(S.you.lvl) ? 1 : 0;
  const nf = nextFloor(S);
  const f = nf && FLOORS.find((x) => x.id === nf);
  s += f && c >= f.price ? 1 : 0;
  for (const it of ITEMS) s += c >= it.price ? 1 : 0;
  s += S.gems >= 5 ? 1 : 0;
  s += S.energy >= 2 ? 1 : 0;
  s += S.stats.taps >= 20 ? 1 : 0;
  s += S.stats.taps >= 10 ? 1 : 0;
  s += dailyClaimable().length;
  if (S.pending) s += eventView(S).choices.map((x) => (x.disabled ? 0 : 1)).join('');
  s += Object.keys(S.buffs).map((k) => (S.buffs[k] > 0 ? 1 : 0)).join('');
  return s;
}
function render(force) {
  if (!S) return;
  const sig = [S.rev, UI.tab, UI.sub.studio, UI.sub.drop, UI.sel, affordSig(), UI.ecoVer || 0, UI.hubVer || 0, LANG, NET.online, NET.mode, S.sel.genre, S.sel.tier, S.sel.name].join('|');
  if (!force && sig === UI.lastSig) return;
  // don't rebuild the page under someone typing a donation amount (keyboard would close)
  if (!force && document.activeElement && (document.activeElement.id === 'donin' || document.activeElement.id === 'refin')) { $('#hud').innerHTML = hudHTML(); return; }
  UI.lastSig = sig;
  const main = $('#main');
  const where = UI.tab + ':' + (UI.sub[UI.tab] || '');
  const keep = UI.renderedTab === where ? main.scrollTop : 0;
  $('#hud').innerHTML = hudHTML();
  $('#tabs').innerHTML = tabsHTML();
  main.innerHTML = '<div class="page" data-tab="' + UI.tab + '">' + tabHTML() + '</div>';
  if (UI.renderedTab !== where) main.firstChild.classList.add('enter');
  main.scrollTop = keep;
  UI.renderedTab = where;
  bindCanvases();
  live();
  syncBackButton();
}

/* ---------- canvases: office scene (+ bloom layer) and tower ---------- */
let SCENE = null, SCENE_OFF = null, SCENE_IMG = null, TOWER_B = null, TOWER_IMG = null;
function bindCanvases() {
  if (!SCENE) {
    SCENE = createScene();
    SCENE_OFF = document.createElement('canvas');
    SCENE_OFF.width = SCN.W;
    SCENE_OFF.height = SCN.H;
    SCENE_IMG = new ImageData(new Uint8ClampedArray(SCENE.frame.d.buffer), SCN.W, SCN.H);
  }
  UI.sceneCv = $('#scene');
  UI.bloomCv = $('#bloom');
  UI.towerCv = $('#tower');
  UI.sceneCtx = UI.sceneCv ? UI.sceneCv.getContext('2d') : null;
  UI.bloomCtx = UI.bloomCv ? UI.bloomCv.getContext('2d') : null;
  UI.towerCtx = UI.towerCv ? UI.towerCv.getContext('2d') : null;
  if (UI.sceneCv) sizeScene();
  drawScene(performance.now() / 1000);
  drawTowerCv(performance.now() / 1000);
}
// the scene canvas fills its box; the room is drawn "contain"-fitted with a slight zoom (sides of the floor cropped)
const CAM = { sx: 26, sy: 0, sw: 204, sh: 176 };
function sizeScene() {
  const cv = UI.sceneCv;
  if (!cv) return;
  const w = cv.clientWidth || 300, h = cv.clientHeight || 220;
  const dpr = Math.min(3, window.devicePixelRatio || 1);
  const W = Math.round(w * dpr), H = Math.round(h * dpr);
  if (cv.width !== W || cv.height !== H) { cv.width = W; cv.height = H; }
  if (UI.bloomCv) {
    const bw = Math.max(1, Math.round(w / 3)), bh = Math.max(1, Math.round(h / 3));
    if (UI.bloomCv.width !== bw || UI.bloomCv.height !== bh) { UI.bloomCv.width = bw; UI.bloomCv.height = bh; }
  }
  UI.sceneSize = w + 'x' + h;
}
function camFit(W, H, crisp) {
  let s = Math.min(W / CAM.sw, H / CAM.sh);
  if (crisp && s >= 3) s = Math.floor(s);
  const dw = CAM.sw * s, dh = CAM.sh * s;
  return [Math.round((W - dw) / 2), Math.round((H - dh) / 2), Math.round(dw), Math.round(dh)];
}
function sceneState() {
  const canHire = {};
  for (const r of ROLES) canHire[r] = S.staff[r].lvl === 0 && S.cash >= ROLE[r].hire;
  return {
    f: S.clock / DAY_SEC,
    working: !!S.project && (hiredCount(S) > 0 || UI.tapFlash > 0),
    staff: S.staff, items: S.items, turbo: S.buffs.turbo > 0, canHire,
    tapFlash: UI.tapFlash, floors: S.floors, hype: S.buffs.hype > 0,
  };
}
function drawScene(t) {
  if (!SCENE || !UI.sceneCtx || UI.tab !== 'office') return;
  try {
    const cv = UI.sceneCv;
    if (UI.sceneSize !== cv.clientWidth + 'x' + cv.clientHeight) sizeScene();
    const tier = themeTier(S.floors);
    if (UI.sceneTier !== undefined && tier > UI.sceneTier) movedIn(tier);
    UI.sceneTier = tier;
    SCENE.render(sceneState(), t);
    SCENE_OFF.getContext('2d').putImageData(SCENE_IMG, 0, 0);
    const ctx = UI.sceneCtx;
    ctx.imageSmoothingEnabled = false;
    ctx.clearRect(0, 0, cv.width, cv.height);
    const f = camFit(cv.width, cv.height, true);
    ctx.drawImage(SCENE_OFF, CAM.sx, CAM.sy, CAM.sw, CAM.sh, f[0], f[1], f[2], f[3]);
    if (UI.bloomCtx) {
      const b = UI.bloomCv, g = camFit(b.width, b.height, false);
      UI.bloomCtx.clearRect(0, 0, b.width, b.height);
      UI.bloomCtx.drawImage(SCENE_OFF, CAM.sx, CAM.sy, CAM.sw, CAM.sh, g[0], g[1], g[2], g[3]);
    }
  } catch (e) {}
}
function drawTowerCv(t) {
  if (!UI.towerCtx || UI.tab !== 'empire') return;
  try {
    if (!TOWER_B) {
      TOWER_B = new Buf(80, 124);
      TOWER_IMG = new ImageData(new Uint8ClampedArray(TOWER_B.d.buffer), 80, 124);
    }
    drawTower(TOWER_B, S.floors, t);
    UI.towerCtx.putImageData(TOWER_IMG, 0, 0);
  } catch (e) {}
}

/* ---------- tapping ---------- */
function onWorkDown(e) {
  if (e && e.isTrusted === false) return;
  ensureAudio();
  if (!MUSIC.on) musicSync();
  noteTapAttempt(true);
  const ms = Math.max(0, Math.min(Math.floor(TICK * 1000) - 1, Math.floor((UI.acc || 0) * 1000)));
  const r = netAct('tap', ms);
  const btn = $('#workbtn');
  const rect = btn ? btn.getBoundingClientRect() : { left: 100, top: 300, width: 100, height: 50 };
  const x = e && e.clientX ? e.clientX : rect.left + rect.width / 2;
  const y = e && e.clientY ? e.clientY : rect.top + rect.height / 2;
  if (r.ok) {
    const tp = tapPower(S);
    UI.tapFlash = 0.5;
    sfx('tap');
    haptic('light');
    floatText(x, y - 10, '+$' + fmtSmall(tp.cash), 'cash');
    if (Math.random() < 0.35) burst(x, y, 5, ['#ffd23f', '#3fd0ff', '#ff3fd0']);
    comboTap(x, y);
    if (btn) { btn.classList.remove('hit'); void btn.offsetWidth; btn.classList.add('hit'); }
  } else if (performance.now() - (UI.tiredAt || 0) > 1800) {
    UI.tiredAt = performance.now();
    toast(L('err.energy_tap'), 'bad', 'bolt');
  }
}

/* ---------- actions ---------- */
function result(r, okText) {
  if (r && r.ok) {
    if (okText) toast(okText, 'good');
    haptic('medium');
  } else errToast(r && r.err);
  render();
  return r && r.ok;
}
function go(tab, sub) {
  if (UI.modal && !UI.modal.sticky) closeModal();
  if (tab === 'drop' && !sub) sub = UI.sub.drop;
  UI.tab = tab;
  if (sub) UI.sub[tab] = sub;
  render(true);
  if (tab === 'drop') loadHub(UI.sub.drop);
}
const ACT = {
  close: () => closeModal(),
  tab: (t) => { if (UI.tab === t && t === 'office') return; go(t); },
  go: (t, s) => go(t, s),
  sub: (g, s) => { UI.sub[g] = s; render(true); if (g === 'drop') loadHub(s); },
  work: () => { if (UI.tab !== 'office') go('office'); const b = $('#workbtn'); if (b) { b.classList.add('pulse'); setTimeout(() => b.classList.remove('pulse'), 1200); } },
  sel: (r) => { UI.sel = r; render(); },
  help: () => showHelp(),
  tut: (what) => {
    if (what === 'next') tutNext();
    else if (what === 'skip') tutStop();
    else if (what === 'go') tutGoto();
    else if (what === 'restart') { closeModal(); tutStart(); }
  },
  music: () => { S.settings.music = S.settings.music === false; savePrefs(); ensureAudio(); musicSync(); showSettings(); },
  mnext: () => { ensureAudio(); if (S.settings.music === false) { S.settings.music = true; savePrefs(); } musicNext(false); musicSync(); showSettings(); },
  pump: () => { UI.sel = 'you'; go('studio', 'team'); },
  hire: (r) => { if (result(netAct('hire', r), L('toast.hired', { role: '$role.' + r + '.name' }))) confettiSmall(); },
  ups: (r) => result(netAct('ups', r), L('toast.upgraded')),
  upy: () => result(netAct('upy'), L('toast.upgraded')),
  buy: (id) => result(netAct('buy', id), L('toast.bought', { item: '$item.' + id + '.name' })),
  flr: (id) => { const r = netAct('flr', id); if (r.ok) showFloorBuild(id); else result(r); },
  bst: (id) => result(netAct('bst', id), L('toast.boost', { name: '$boost.' + id + '.name' })),
  genre: (g) => { UI.genrePicked = true; S.sel.genre = g; S.sel.name = GENRES[g].names[UI.nameSeed++ % GENRES[g].names.length]; savePrefs(); render(); },
  tier: (t) => {
    if (!tierUnlocked(S, t)) { toast(L('toast.tier_locked', { name: '$floor.' + TIERS[t].need + '.name', sub: '$floor.' + TIERS[t].need + '.sub' }), 'bad', 'lock'); return; }
    S.sel.tier = t; savePrefs(); render();
  },
  rename: () => { const n = GENRES[S.sel.genre].names; S.sel.name = n[UI.nameSeed++ % n.length]; savePrefs(); render(); },
  start: () => {
    const r = netAct('new', S.sel.genre, S.sel.tier, S.sel.name);
    if (r.ok) { toast(L('toast.started', { name: S.project.name }), 'good'); go('office'); ACT.work(); } else errToast(r.err);
  },
  auto: () => { netAct('auto', !S.auto); render(); },
  claim: (id) => {
    const r = netAct('clm', id);
    if (r.ok) { toast(L('toast.reward', { r: goalReward(r.goal.rw) }), 'good', 'star'); confettiSmall(); haptic('ok'); if (r.goal.rw.cash) centerFly('.pill.cash', I('coin', 16), 6); if (r.goal.rw.gems) centerFly('.pill.gem', I('gem', 16), 3); centerFly('.pill.pts', I('pts', 14), 3); } else errToast(r.err);
    render();
  },
  dly: (id) => { const r = netAct('dly', id); if (r.ok) { toast(L('toast.daily', { cash: money(dailyCash(S)) }), 'good'); haptic('ok'); centerFly('.pill.cash', I('coin', 16), 6); } else errToast(r.err); render(); },
  event: () => modalOrQueue(showEvent),
  ev: (i) => {
    const r = netAct('ev', Number(i));
    if (!r.ok) return errToast(r.err);
    haptic('medium');
    if (UI.modal && UI.modal.id === 'event') {
      openModal(outcomeCard(r.outcome) + `<button class="btn big" data-act="close">${L('ui.ok')}</button>`, { id: 'outcome', mcls: 'evm' });
    }
    render();
  },
  ipo: () => modalOrQueue(showIPO),
  ipogo: () => { closeModal(); const r = netAct('ipo'); if (r.ok) { confetti(160); render(true); } else errToast(r.err); },
  settings: () => showSettings(),
  lang: (code) => {
    setLang(code);
    lsSet('od2_lang', code);
    netApi.lang(code);
    if (UI.modal && UI.modal.id === 'settings') showSettings();
    if (UI.modal && UI.modal.id === 'welcome') showWelcome();
    render(true);
  },
  sound: () => { S.settings.sound = !S.settings.sound; savePrefs(); ensureAudio(); sfx('coin'); showSettings(); },
  speed: (v) => { NET.speed = Number(v) || 1; demoSave(); showSettings(); },
  reset: (step) => {
    if (step !== 'yes') { UI.resetArm = Date.now(); showSettings(); return; }
    if (Date.now() - (UI.resetArm || 0) > 10000) return;
    closeModal(true);
    if (NET.mode === 'demo') { demoReset(); render(true); toast(L('toast.reset'), 'good'); return; }
    netApi.forget().then((r) => {
      if (r && r.ok) { openModal(`<div class="center"><h2 class="h2">${L('set.forgot')}</h2><p class="sub">${L('set.forgot_t')}</p></div>`, { sticky: true }); try { TG && TG.close && setTimeout(() => TG.close(), 2500); } catch (e) {} }
      else errToast(r && r.err);
    });
  },
  start_game: () => { closeModal(true); lsSet('od2_intro', '1'); ensureAudio(); musicSync(); sfx('up'); if (S.stats.started === 0) tutStart(); },
  checkin: () => {
    if (UI.ciBusy) return;
    UI.ciBusy = true;
    netApi.checkin().then((r) => {
      UI.ciBusy = false;
      if (r && r.ok) {
        lsSet('od2_ci', String(Math.floor(Date.now() / 86400000)));
        if (UI.modal && UI.modal.id === 'checkin') closeModal();
        toast(L('toast.checkin', { n: fmtPts(r.pts), s: r.streak }), 'good', 'calendar');
        confetti(90); sfx('pts'); haptic('ok'); centerFly('.pill.pts', I('pts', 16), 8);
        UI.ecoVer = (UI.ecoVer || 0) + 1;
        render(true);
        loadHub('tasks', true);
      } else errToast(r && r.err);
    });
  },
  share: () => {
    const link = NET.eco && NET.eco.ref && NET.eco.ref.link;
    if (!link) return;
    const url = 'https://t.me/share/url?url=' + encodeURIComponent(link) + '&text=' + encodeURIComponent(L('fr.share_text'));
    if (TG && TG.openTelegramLink) TG.openTelegramLink(url);
    else ACT.copy();
  },
  copy: () => {
    const link = NET.eco && NET.eco.ref && NET.eco.ref.link;
    if (!link) return;
    copyText(link, (ok) => toast(ok ? L('toast.copied') : L('toast.copy_fail'), ok ? 'good' : 'bad', 'copy'));
  },
  task: (what, id) => {
    const t = (UI.hub.tasks || []).find((x) => x.id === id);
    if (what === 'go' && t) {
      if (t.url) openLink(t.url);
      if (t.kind === 'visit') netApi.taskStart(id).then(() => setTimeout(() => loadHub('tasks', true), 9000));
      else if (t.kind === 'channel') netApi.taskStart(id);
      return;
    }
    if (what === 'claim') {
      netApi.taskClaim(id).then((r) => {
        if (r && r.tasks) UI.hub.tasks = r.tasks;
        if (r && r.ok) { toast(L('toast.task', { n: fmtPts(r.pts) }), 'good', 'pts'); confettiSmall(); sfx('pts'); haptic('ok'); }
        else errToast(r && r.err);
        UI.hubVer = (UI.hubVer || 0) + 1;
        render();
      });
    }
  },
  pay: (sku) => buySku(sku),
  payton: (sku) => buyTon(sku),
  refclaim: () => {
    const el = $('#refin');
    const code = el ? el.value.trim() : '';
    if (!code) return;
    netApi.refClaim(code).then((r) => {
      if (r && r.eco) setEco(r.eco);
      if (r && r.ok) { toast(L('fr.code_ok', { cash: money(r.cash) }), 'good', 'gift'); confetti(120); haptic('ok'); netSync({ full: true }); loadHub('friends', true); }
      else errToast(r && r.err);
      UI.hubVer = (UI.hubVer || 0) + 1; render(true);
    });
  },
  donamt: (n) => { UI.donAmt = Number(n); UI.hubVer = (UI.hubVer || 0) + 1; render(true); },
  donstars: () => donateStars(),
  donton: () => donateTon(),
  ad: () => {
    if (UI.adBusy) return;
    const e = NET.eco && NET.eco.ads;
    if (e && e.next > Date.now()) return toast(L('err.ads_wait', { s: Math.ceil((e.next - Date.now()) / 1000) }), 'bad');
    UI.adBusy = true;
    musicStop();
    showAd().then((r) => {
      UI.adBusy = false;
      musicSync();
      if (r && r.ok) {
        toast(r.callback ? L('ads.ok_cb') : L('ads.ok', { cash: money(r.cash), pts: r.pts }), 'good', 'bolt');
        confettiSmall(); sfx('coin'); haptic('ok'); centerFly('.pill.cash', I('coin', 16), 6); centerFly('.pill.pts', I('pts', 14), 3);
        UI.ecoVer = (UI.ecoVer || 0) + 1;
        setTimeout(() => netSync({ full: true }), 600);
      } else if (r && r.err === 'ads_wait' && r.next) toast(L('err.ads_wait', { s: Math.ceil((r.next - Date.now()) / 1000) }), 'bad');
      else if (r && r.err !== 'ads_skip') errToast(r.err);
      else toast(L('ads.skipped'), '');
      render(true);
    });
  },
  wallet: (what) => {
    if (what === 'connect') {
      tonConnect().then((r) => { if (!r.ok) { if (r.err === 'tonconnect' || r.err === 'not_configured') showManualWallet(); else errToast(r.err); } });
    } else if (what === 'off') {
      tonDisconnect().then((r) => { UI.walletFriendly = ''; if (r && r.ok) toast(L('wallet.off_ok'), 'good'); render(true); });
    } else if (what === 'manual') showManualWallet();
    else if (what === 'save') {
      const inp = $('#waddr');
      const v = inp ? inp.value.trim() : '';
      if (!v) return;
      netApi.wallet({ address: v }).then((r) => { UIH.onWallet(r); if (r && r.ok) closeModal(); });
    }
  },
  chal: (ans) => answerChallenge(ans),
  retry: () => location.reload(),
  openbot: () => { if (NET.cfg && NET.cfg.bot) openLink('https://t.me/' + NET.cfg.bot + (NET.cfg.app ? '/' + NET.cfg.app : '')); },
  link: (k) => { const l = NET.cfg && NET.cfg.links && NET.cfg.links[k]; if (l) openLink(l); },
};
function confettiSmall() { confetti(40); }

/* ---------- hub data ---------- */
function loadHub(sub, force) {
  const now = Date.now();
  const key = sub === 'top' ? 'board' : sub === 'token' ? 'donate' : sub;
  if (!['tasks', 'friends', 'board', 'shop', 'home', 'donate'].includes(key)) return;
  if (key === 'home') { if (NET.mode === 'online' && (force || now - (UI.hub.loadedAt.home || 0) > 30000)) { UI.hub.loadedAt.home = now; netSync(); } if (!UI.hub.tasks) loadHub('tasks'); return; }
  if (!force && UI.hub[key] && now - (UI.hub.loadedAt[key] || 0) < 20000) return;
  UI.hub.loadedAt[key] = now;
  const p = key === 'tasks' ? netApi.tasks() : key === 'friends' ? netApi.friends() : key === 'board' ? netApi.leaderboard() : key === 'donate' ? netApi.donateInfo() : netApi.shop();
  p.then((r) => {
    if (!r || !r.ok) return;
    if (key === 'tasks') UI.hub.tasks = r.tasks;
    else if (key === 'friends') UI.hub.friends = r;
    else if (key === 'board') UI.hub.board = r;
    else if (key === 'donate') UI.hub.donate = r;
    else UI.hub.shop = r;
    UI.hubVer = (UI.hubVer || 0) + 1;
    render();
  });
}
function paidJuice() {
  toast(L('shop.paid'), 'good', 'gem');
  confetti(140); sfx('floor'); haptic('ok');
  [1500, 4000, 9000].forEach((ms) => setTimeout(() => netSync({ full: true }), ms));
  setTimeout(() => loadHub('shop', true), 2500);
}
/* ---------- donations toward the launch ---------- */
function donAmount() {
  const d = UI.hub.donate || {};
  const v = Math.floor(Number(UI.donAmt || 100));
  return Math.max(d.min || 100, Math.min(d.max || 10000, v || 0));
}
function donated() {
  toast(L('don.thanks'), 'good', 'heart');
  confetti(160); sfx('floor'); haptic('ok');
  [2000, 6000].forEach((ms) => setTimeout(() => loadHub('token', true), ms));
  setTimeout(() => netSync({ full: true }), 3000);
}
function donateStars() {
  if (NET.mode !== 'online') return toast(L('shop.demo'), 'bad');
  const n = donAmount();
  netApi.donateInvoice(n).then((r) => {
    if (!r || !r.ok || !r.url) return errToast((r && r.err) || 'invoice');
    const after = (status) => { if (status === 'paid') donated(); else if (status === 'failed') errToast('pay_failed'); };
    if (TG && TG.openInvoice) TG.openInvoice(r.url, after); else openLink(r.url);
  });
}
async function donateTon() {
  if (NET.mode !== 'online') return toast(L('shop.demo'), 'bad');
  if (UI.tonBusy) return toast(L('shop.ton_wait'), '');
  const d = UI.hub.donate || {};
  const ton = Math.max(d.tonMin || 1, Math.round(donAmount() * (d.tonPerStar || 0.011) * 10) / 10);
  UI.tonBusy = true;
  try {
    const r = await netApi.donateTon(ton);
    if (!r || !r.ok || !r.order) return errToast((r && r.err) || 'unavailable');
    const p = await tonPay(r.order);
    if (!p.ok) { if (p.err === 'need_wallet') { toast(L('shop.ton_connect'), '', 'ton'); tonConnect(); } else if (p.err === 'ton_rejected') toast(L('err.ton_rejected'), 'bad'); else errToast(p.err); return; }
    toast(L('shop.ton_wait'), '', 'ton');
    for (let i = 0; i < 30; i++) {
      await new Promise((res) => setTimeout(res, 4000));
      const c = await netApi.tonCheck(r.order.id);
      if (c && c.paid) return donated();
      if (c && c.expired) break;
    }
    toast(L('shop.ton_later'), '', 'ton');
  } finally { UI.tonBusy = false; }
}
async function buyTon(sku) {
  if (NET.mode !== 'online') return toast(L('shop.demo'), 'bad');
  if (UI.tonBusy) return toast(L('shop.ton_wait'), '');
  UI.tonBusy = true;
  try {
    const r = await netApi.tonOrder(sku);
    if (!r || !r.ok || !r.order) return errToast((r && r.err) || 'unavailable');
    const p = await tonPay(r.order);
    if (!p.ok) {
      if (p.err === 'need_wallet') { toast(L('shop.ton_connect'), '', 'ton'); tonConnect(); }
      else if (p.err === 'ton_rejected') toast(L('err.ton_rejected'), 'bad');
      else errToast(p.err);
      return;
    }
    toast(L('shop.ton_wait'), '', 'ton');
    // the transfer needs a few seconds to land in a block; then the server sees it
    for (let i = 0; i < 30; i++) {
      await new Promise((res) => setTimeout(res, 4000));
      const c = await netApi.tonCheck(r.order.id);
      if (c && c.paid) return paidJuice();
      if (c && c.expired) break;
    }
    toast(L('shop.ton_later'), '', 'ton');
  } finally { UI.tonBusy = false; }
}
function buySku(sku) {
  if (NET.mode !== 'online') return toast(L('shop.demo'), 'bad');
  netApi.invoice(sku).then((r) => {
    if (!r || !r.ok || !r.url) return errToast((r && r.err) || 'invoice');
    const after = (status) => {
      if (status === 'paid') paidJuice();
      else if (status === 'failed') errToast('pay_failed');
    };
    if (TG && TG.openInvoice) TG.openInvoice(r.url, after);
    else openLink(r.url);
  });
}

/* ---------- modals ---------- */
function showEvent() {
  if (!S.pending) return;
  openModal(eventCard(true), { id: 'event', mcls: 'evm' });
  sfx('coin');
}
/* ---------- moving into a new floor: the office gets a new look ---------- */
function movedIn(tier) {
  const box = $('.scene');
  if (box) { box.classList.remove('moving'); void box.offsetWidth; box.classList.add('moving'); setTimeout(() => box.classList.remove('moving'), 1300); }
  const id = 'f' + tier;
  setTimeout(() => { sayBubble('you', `<b>${esc(L('floor.' + id + '.sub'))}</b><br>${esc(L('fb.moved'))}`); sfx('up'); }, 700);
}
/* ---------- floor purchase: the tower builds the new floor ---------- */
function showFloorBuild(id) {
  const row = 5 - Number(id.slice(1)); // f5 → 0 … f1 → 4
  openModal(`<div class="rel fbuild">
    <small class="px glow-t">${L('fb.title')}</small>
    <canvas class="tower fb-cv" width="80" height="124"></canvas>
    <h2 class="h2 big">${L('floor.' + id + '.name')} · ${L('floor.' + id + '.sub')}</h2>
    <p class="ev-text fb-perk">${L('floor.' + id + '.perk')}</p>
    <p class="sub">🎨 ${L('fb.look')}</p>
    <button class="btn big glow" data-act="close">${L('fb.ok')}</button>
  </div>`, { id: 'floor', mcls: 'relm' });
  const cv = $('.fb-cv');
  const ctx = cv && cv.getContext('2d');
  if (!ctx) return;
  const B = new Buf(80, 124);
  const img = new ImageData(new Uint8ClampedArray(B.d.buffer), 80, 124);
  const t0 = performance.now(), DUR = 2200;
  let done = false, lastBeat = -1;
  sfx('up');
  const frame = (now) => {
    if (!cv.isConnected) return;
    const k = Math.min(1, (now - t0) / DUR);
    try { drawTower(B, S.floors, now / 1000, { row, k }); ctx.putImageData(img, 0, 0); } catch (e) {}
    const beat = Math.floor(k * 6);
    if (beat !== lastBeat && k < 1) { lastBeat = beat; haptic('light'); if (beat) sfx('tap'); }
    if (k >= 1 && !done) {
      done = true;
      cv.classList.add('done');
      confetti(140); haptic('ok'); sfx('floor');
      $('.fb-perk') && $('.fb-perk').classList.add('lit');
    }
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);
}
function showRelease(d) {
  const g = d.game;
  const stars = Math.round(g.score / 2);
  let st = '';
  for (let i = 1; i <= 5; i++) st += `<span class="${i <= stars ? 'on' : ''}">${I('star', 18)}</span>`;
  openModal(`<div class="rel">
    <small class="px glow-t">${L('rel.title')}</small>
    <div class="rel-cover">${imgTag(coverURL(g.genre, g.name), 'cover xl')}<span class="stamp px ${scoreClass(g.score)}">${g.score.toFixed(1)}</span></div>
    <h2 class="h2 big">${esc(g.name)}</h2>
    <p class="sub">${L('genre.' + g.genre)} · ${L('tier.' + g.tier)}</p>
    <div class="stars">${st}</div>
    <p class="ev-text">${L(g.score >= 9 ? 'rel.hit' : g.score >= 7 ? 'rel.good' : g.score >= 5 ? 'rel.fine' : 'rel.bad')}</p>
    <div class="stats3">
      <div><small>${L('rel.forecast')}</small><b>${money(g.total)}</b></div>
      <div><small>${L('rel.rep')}</small><b class="${d.repGain > 0 ? 'grn' : d.repGain < 0 ? 'red' : ''}">${d.repGain > 0 ? '+' : ''}${(Math.round(d.repGain * 10) / 10)}</b></div>
      <div><small>${L('res.gems')}</small><b class="mag">${d.gemGain ? '+' + d.gemGain : '0'}</b></div>
    </div>
    <button class="btn big glow" data-act="close">${L('rel.ok')}</button>
  </div>`, { id: 'release', mcls: 'relm' });
  if (g.score >= 7) confetti(g.score >= 9 ? 180 : 110);
  haptic('ok');
}
function showWelcome() {
  openModal(`<div class="welcome">
    <div class="w-hero"><img src="${heroURL()}" alt="" width="256" height="176"><div class="w-logo px">OFFICE<br>DREAMS</div></div>
    <p class="ev-text">${L('intro.text')}</p>
    <ul class="bul">
      <li>${I('bolt', 14, 'yel')} ${L('intro.b1')}</li>
      <li>${I('games', 14, 'cy')} ${L('intro.b2')}</li>
      <li>${I('pts', 14)} ${L('intro.b3')}</li>
      <li>${I('users', 14, 'grn')} ${L('intro.b4')}</li>
    </ul>
    <div class="langs">${LANG_LIST.map((c) => `<button class="${LANG === c ? 'on' : ''}" data-act="lang:${c}">${L('lang.' + c)}</button>`).join('')}</div>
    <button class="btn big glow" data-act="start_game">${L('intro.go')}</button>
  </div>`, { id: 'welcome', sticky: true, mcls: 'wm' });
}
function showSettings() {
  const armed = Date.now() - (UI.resetArm || 0) < 10000;
  const links = NET.cfg && NET.cfg.links ? NET.cfg.links : {};
  openModal(`<h2 class="h2">${I('gear', 16)} ${L('set.title')}</h2>
    <div class="setrow"><span>${I('globe', 16)} ${L('set.lang')}</span><div class="langs">${LANG_LIST.map((c) => `<button class="${LANG === c ? 'on' : ''}" data-act="lang:${c}">${L('lang.' + c)}</button>`).join('')}</div></div>
    <div class="setrow"><span>${I(S.settings.sound ? 'sound' : 'mute', 16)} ${L('set.sound')}</span><button class="toggle" data-act="sound" aria-pressed="${!!S.settings.sound}"><span class="sw"></span></button></div>
    <div class="setrow"><span>${I('sound', 16)} ${L('set.music')}</span><button class="toggle" data-act="music" aria-pressed="${S.settings.music !== false}"><span class="sw"></span></button></div>
    <div class="setrow"><span class="mut">♪ ${L('music.' + TRACKS[MUSIC.track].id)}</span><button class="btn ghost sm" data-act="mnext">${L('set.next_track')} ›</button></div>
    <div class="setrow"><span>${I('info', 16)} ${L('help.title')}</span><button class="btn ghost sm" data-act="help">${L('ui.open')}</button></div>
    ${NET.mode === 'demo' ? `<div class="setrow"><span>${I('bolt', 16)} ${L('set.speed')}</span><div class="langs">${[1, 2, 5].map((v) => `<button class="${NET.speed === v ? 'on' : ''}" data-act="speed:${v}">×${v}</button>`).join('')}</div></div>` : ''}
    ${links.channel || links.chat ? `<div class="setrow"><span>${I('plane', 16)} ${L('set.community')}</span><div class="langs">${links.channel ? `<button data-act="link:channel">${L('set.channel')}</button>` : ''}${links.chat ? `<button data-act="link:chat">${L('set.chat')}</button>` : ''}</div></div>` : ''}
    <div class="about"><p class="sub">${L('set.about')}</p>
      ${NET.me ? `<p class="mut sm">ID ${NET.me.id} · v${BUILD.version}</p>` : `<p class="mut sm">${L('net.demo')} · v${BUILD.version}</p>`}</div>
    ${armed ? `<div class="danger"><p class="sub">${L(NET.mode === 'demo' ? 'set.reset_q' : 'set.forget_q')}</p><button class="btn red" data-act="reset:yes">${L('set.confirm')}</button></div>`
      : `<button class="linkbtn red" data-act="reset">${L(NET.mode === 'demo' ? 'set.reset' : 'set.forget')}</button>`}`, { id: 'settings' });
}
function showHelp() {
  const sym = esc((NET.cfg && NET.cfg.symbol) || 'OFFICE');
  openModal(`<h2 class="h2">${I('info', 16)} ${L('help.title')}</h2>
    <div class="help">
      <h3 class="h3">${L('help.play')}</h3>
      <ol class="steps">
        <li>${L('help.p1')}</li><li>${L('help.p2')}</li><li>${L('help.p3')}</li><li>${L('help.p4')}</li><li>${L('help.p5')}</li><li>${L('help.p6')}</li><li>${L('help.p7')}</li>
      </ol>
      <h3 class="h3">${L('help.money')}</h3>
      <ul class="cur">
        <li>${I('coin', 18)}<div><b>${L('res.cash')}</b><small>${L('help.cash')}</small></div></li>
        <li>${I('gem', 18)}<div><b>${L('res.gems')}</b><small>${L('help.gems')}</small></div></li>
        <li>${I('pts', 18)}<div><b>${L('res.pts')}</b><small>${L('help.pts', { sym })}</small></div></li>
        <li>${I('ton', 18)}<div><b>${sym}</b><small>${L('help.token', { sym })}</small></div></li>
      </ul>
    </div>
    <div class="row2"><button class="btn" data-act="go:drop:token">${L('help.more_token')}</button><button class="btn ghost" data-act="tut:restart">${L('help.tutorial')}</button></div>`, { id: 'help' });
}
function showOffline(sec, cash) {
  openModal(`<div class="center">
    ${I('coin', 34)}
    <h2 class="h2">${L('off.title')}</h2>
    <p class="sub">${L('off.text', { t: fmtDur(sec) })}</p>
    <div class="bigcash px">+${money(cash)}</div>
    <p class="hint">${L('off.hint')}</p>
    <button class="btn big glow" data-act="close">${L('off.ok')}</button></div>`, { id: 'offline' });
  sfx('coin');
}
function showCheckin() {
  if (!checkinReady()) return;
  const e = NET.eco;
  openModal(`<div class="center">
    ${I('calendar', 30)}
    <h2 class="h2">${L('hub.checkin')}</h2>
    <p class="sub">${L('ci.text')}</p>
    ${checkinStrip(e)}
    <button class="btn big glow" data-act="checkin">${L('hub.checkin_btn', { n: e.checkin.next })}</button></div>`, { id: 'checkin' });
}
function showIPO() {
  openModal(`<div class="center">${I('crown', 34)}
    <h2 class="h2">${L('ipo.title')}</h2>
    <p class="ev-text">${L('ipo.confirm', { gems: 3 + S.legacy })}</p>
    <div class="row2"><button class="btn ghost" data-act="close">${L('ui.cancel')}</button><button class="btn gold" data-act="ipogo">${L('ipo.btn')}</button></div></div>`, { id: 'ipo' });
}
function showEnding() {
  openModal(`<div class="center ending">
    <img class="hero" src="${heroURL()}" alt="" width="256" height="176">
    <h2 class="h2 big">${L('end.title')}</h2>
    <p class="ev-text">${L('end.text')}</p>
    <button class="btn big gold" data-act="close">${L('end.ok')}</button></div>`, { id: 'ending' });
  confetti(200);
}
function showManualWallet() {
  openModal(`<h2 class="h2">${I('ton', 16)} ${L('wallet.manual')}</h2>
    <p class="sub">${L('wallet.manual_t')}</p>
    <input id="waddr" class="inp" placeholder="UQ…" autocomplete="off" spellcheck="false" maxlength="80">
    <button class="btn big" data-act="wallet:save">${L('wallet.save')}</button>`, { id: 'wallet' });
  const inp = $('#waddr');
  if (inp) inp.addEventListener('keydown', (e) => e.stopPropagation());
}
function showChallenge(ch) {
  if (!ch || (UI.modal && UI.modal.id === 'chal:' + ch.id)) return;
  UI.chal = ch;
  let body;
  if (ch.kind === 'odd') {
    body = `<p class="ev-text">${L('chal.odd')}</p><div class="cgrid">${ch.cells.map((c, i) => `<button class="cell" data-act="chal:${i}">${c}</button>`).join('')}</div>`;
  } else {
    body = `<p class="ev-text">${L('chal.count', { e: ch.target })}</p><div class="cgrid view">${ch.cells.map((c) => `<span class="cell">${c}</span>`).join('')}</div>
      <div class="copts">${ch.options.map((o) => `<button class="btn" data-act="chal:${o}">${o}</button>`).join('')}</div>`;
  }
  const show = () => openModal(`<h2 class="h2">${I('shield', 16)} ${L('chal.title')}</h2><p class="sub">${L('chal.sub')}</p>${body}`, { id: 'chal:' + ch.id, sticky: true });
  if (UI.modal && !UI.modal.sticky) { UI.modal = null; show(); } else modalOrQueue(show);
}
function answerChallenge(ans) {
  const ch = UI.chal;
  if (!ch) return;
  UI.chal = null;
  netApi.challenge(ch.id, Number(ans)).then((r) => {
    closeModal(true);
    if (r && r.ok) { toast(L('chal.ok'), 'good', 'shield'); haptic('ok'); }
    else { toast(L('chal.bad'), 'bad', 'shield'); haptic('err'); setTimeout(() => netSync(), 1200); }
  });
}

function releaseJuice(d) {
  const sc = $('.scene');
  const r = sc ? sc.getBoundingClientRect() : { left: innerWidth / 2, top: innerHeight / 3, width: 0, height: 0 };
  const x = r.left + r.width / 2, y = r.top + r.height / 2;
  flyTo(x, y, '.pill.cash', I('coin', 16), d.game.score >= 8 ? 12 : 7);
  if (d.gemGain) flyTo(x, y, '.pill.gem', I('gem', 16), 3);
  if (d.game.score >= 8) shake(d.game.score >= 9);
}
function centerFly(sel, html, n) { flyTo(innerWidth / 2, innerHeight * 0.45, sel, html, n); }

/* ---------- engine → UI events ---------- */
hooks.emit = function (type, data) {
  if (!UI.booted) return;
  if (type === 'sfx') sfx(data);
  else if (type === 'release') {
    releaseJuice(data);
    // a big celebration for the first releases and for hits; later routine releases get a toast
    if (S.stats.released <= 3 || data.game.score >= 8.5 || data.gemGain) modalOrQueue(() => showRelease(data));
    else { toast(L('toast.release', { name: data.game.name, score: data.game.score.toFixed(1) }), data.game.score >= 7 ? 'good' : '', 'games'); if (data.game.score >= 7) confettiSmall(); }
  }
  else if (type === 'goal') { toast(L('toast.goal', { t: '$goal.' + data.id }), 'good', 'star'); }
  else if (type === 'event') { toast(L('toast.event', { t: '$ev.' + data.id + '.title' }), 'good', 'quest'); modalOrQueue(showEvent); }
  else if (type === 'day') toast(L('toast.day', { n: data.day, inc: money(data.income), sal: money(data.salary) }), '');
  else if (type === 'toast') toast(L(data.k), data.kind);
  else if (type === 'ending') modalOrQueue(showEnding);
  else if (type === 'ipo') toast(L('toast.ipo', { n: data.n }), 'good', 'crown');
  else if (type === 'bug') { sfx('blip'); updateSceneFx(); if (!lsGet('od2_bug_hint')) { lsSet('od2_bug_hint', '1'); toast(L('bug.hint'), 'bad', 'star'); } }
  else if (type === 'bugEscaped') { toast(L('bug.escaped', { n: fmt(data.loss) }), 'bad'); updateSceneFx(); }
  else if (type === 'idea') { sfx('pop'); updateSceneFx(); if (!lsGet('od2_idea_hint')) { lsSet('od2_idea_hint', '1'); toast(L('idea.hint'), 'good', 'star'); } }
};

/* ---------- network → UI ---------- */
UIH.onEco = (e) => {
  UI.ecoVer = (UI.ecoVer || 0) + 1;
  const el = $('#hudpts');
  if (el) countUp(el, e.pts, 900, fmtPts);
  const big = $('#bigpts');
  if (big) countUp(big, e.pts, 900, fmtPts);
};
UIH.onGain = (g) => {
  const el = $('#hudpts');
  if (!el) return;
  const r = el.getBoundingClientRect();
  floatText(r.left + r.width / 2, r.bottom + 4, '+' + fmtPts(g), 'pts');
  const pill = el.closest('.pill');
  if (pill) { pill.classList.remove('flash'); void pill.offsetWidth; pill.classList.add('flash'); }
};
UIH.onState = () => { UI.lastSig = ''; };
UIH.onStatus = () => render(true);
UIH.onOffline = (sec, cash) => { if (cash >= 1) modalOrQueue(() => showOffline(sec, cash)); };
UIH.onChallenge = (ch) => showChallenge(ch);
UIH.onRefAct = () => { toast(L('toast.ref_act'), 'good', 'users'); confetti(100); loadHub('friends', true); };
UIH.onDropped = (err) => toast(L(err === 'clock' ? 'toast.clock' : 'toast.synced'), err === 'clock' ? 'bad' : '');
UIH.onFatal = () => openModal(`<div class="center">${I('info', 30)}<h2 class="h2">${L('boot.session')}</h2><p class="sub">${L('boot.session_t')}</p><button class="btn big" data-act="retry">${L('boot.retry')}</button></div>`, { sticky: true, id: 'fatal' });
UIH.onWallet = (r) => {
  if (r && r.ok) {
    UI.walletFriendly = r.friendly || '';
    toast(L(r.level ? 'wallet.ok_verified' : 'wallet.ok'), 'good', 'ton');
    confettiSmall();
    loadHub('tasks', true);
  } else errToast((r && r.err) || 'address');
  render(true);
};

/* ---------- main loop ---------- */
function frame(now) {
  const dt = Math.min(0.25, Math.max(0, (now - (UI.last || now)) / 1000));
  UI.last = now;
  if (S) {
    UI.acc = (UI.acc || 0) + dt * (NET.mode === 'demo' ? NET.speed : 1);
    let n = 0;
    while (UI.acc >= TICK && n < 40) { UI.acc -= TICK; advanceTicks(S, 1); n++; }
    if (UI.tapFlash > 0) UI.tapFlash = Math.max(0, UI.tapFlash - dt);
    netPump(Date.now());
    UI.liveAcc += dt;
    if (UI.liveAcc >= 0.2) { UI.liveAcc = 0; render(); live(); tutUpdate(); }
    UI.sceneAcc += dt;
    if (UI.sceneAcc >= 0.045) { UI.sceneAcc = 0; drawScene(now / 1000); }
    UI.towerAcc += dt;
    if (UI.towerAcc >= 0.25) { UI.towerAcc = 0; drawTowerCv(now / 1000); }
  }
  requestAnimationFrame(frame);
}

/* ---------- input wiring ---------- */
function wireInput() {
  // donation amount typed by hand: keep it without re-rendering (so the keyboard stays open)
  document.addEventListener('input', (e) => {
    if (!e.target || e.target.id !== 'donin') return;
    UI.donAmt = Number(e.target.value) || 0;
    const n = donAmount();
    const b = $('[data-act="donstars"]');
    if (b) b.textContent = '⭐ ' + L('don.give', { n: fmt(n) });
    const d = UI.hub.donate || {};
    const t = $('[data-act="donton"]');
    if (t && d.ton) t.innerHTML = I('ton', 14) + ' ' + Math.max(d.tonMin || 1, Math.round(n * (d.tonPerStar || 0.011) * 10) / 10) + ' TON';
    document.querySelectorAll('.don-chips .chip').forEach((c) => c.classList.toggle('on', c.dataset.act === 'donamt:' + n));
  });
  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-act]');
    if (!el || el.disabled) return;
    if (el.closest('.ovl') === null && UI.modal && !$('#ovl').hidden) return;
    ensureAudio();
    musicSync();
    const parts = el.dataset.act.split(':');
    const f = ACT[parts[0]];
    if (f) f.apply(null, parts.slice(1));
  });
  // the work button reacts on pointerdown (fast tapping), keyboard via click with detail 0
  document.addEventListener('pointerdown', (e) => {
    const b = e.target.closest('#workbtn');
    if (b) { e.preventDefault(); onWorkDown(e); return; }
    if (e.target.id === 'scene' && e.isTrusted !== false) { ensureAudio(); onSceneTap(e); }
  }, { passive: false });
  document.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && document.activeElement && document.activeElement.id === 'workbtn') { e.preventDefault(); onWorkDown(e); }
    if (e.key === 'Escape' && UI.modal) closeModal();
  });
  $('#ovl').addEventListener('click', (e) => { if (e.target.id === 'ovl') closeModal(); });
  document.addEventListener('visibilitychange', () => {
    musicSync();
    if (document.hidden) netFlush();
    else { UI.last = performance.now(); if (NET.mode === 'online') setTimeout(() => netSync(), 300); }
  });
  window.addEventListener('pagehide', () => netFlush());
  window.addEventListener('resize', () => { sizeScene(); });
  if (TG && TG.BackButton) {
    try {
      TG.BackButton.onClick(() => {
        if (UI.modal) closeModal();
        else if (UI.tab !== 'office') go('office');
      });
    } catch (e) {}
  }
}

/* ---------- boot ---------- */
function splash(text, err) {
  const sp = $('#splash');
  if (!sp) return;
  const st = $('.sp-status', sp);
  if (st) st.textContent = text || '';
  sp.classList.toggle('err', !!err);
}
async function boot() {
  if (TG) {
    try {
      TG.ready();
      TG.expand();
      if (TG.setHeaderColor) TG.setHeaderColor('#060a26');
      if (TG.setBackgroundColor) TG.setBackgroundColor('#040719');
      if (TG.disableVerticalSwipes) TG.disableVerticalSwipes();
    } catch (e) {}
  }
  const tgLang = TG && TG.initDataUnsafe && TG.initDataUnsafe.user && TG.initDataUnsafe.user.language_code;
  setLang(lsGet('od2_lang') || detectLang(tgLang));
  splash(L('boot.connecting'));
  wireInput();
  let res;
  try { res = await netBoot(BUILD.mode === 'demo'); } catch (e) { res = { ok: false, err: 'server' }; }
  if (!res.ok) {
    const sp = $('#splash');
    splash(L('boot.err_' + (res.err === 'no_telegram' ? 'tg' : res.err === 'region' ? 'region' : 'server')), true);
    const box = sp && $('.sp-actions', sp);
    if (box) {
      box.innerHTML = res.err === 'no_telegram' && NET.cfg && NET.cfg.bot
        ? `<button class="btn big glow" data-act="openbot">${L('boot.open_tg')}</button>`
        : res.err === 'region' ? '' : `<button class="btn big" data-act="retry">${L('boot.retry')}</button>`;
    }
    return;
  }
  if (!lsGet('od2_lang') && NET.me && NET.me.lang) setLang(NET.me.lang);
  UI.booted = true;
  tutLoad();
  if (COACH.i < 0 && lsGet('od2_intro')) COACH.i = TUT.length; // players from before the tutorial existed
  render(true);
  const sp = $('#splash');
  if (sp) { sp.classList.add('out'); setTimeout(() => sp.remove(), 700); }
  requestAnimationFrame((t) => { UI.last = t; frame(t); });
  if (!lsGet('od2_intro') && S.stats.taps < 5) setTimeout(showWelcome, 450);
  else if (S.pending) setTimeout(() => modalOrQueue(showEvent), 700);
  if (NET.mode === 'online') { setTimeout(() => loadHub('tasks'), 1500); }
  setTimeout(() => { if (checkinReady() && lsGet('od2_ci') !== String(Math.floor(Date.now() / 86400000))) modalOrQueue(showCheckin); }, 2500);
}
