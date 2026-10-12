/* ============================================================
   OFFICE DREAMS — network layer
   online: Telegram auth + authoritative server sync (sync_client.js)
   demo:   everything local (artifact preview / opened outside Telegram
           without a server) — points are simulated, nothing is shared
   ============================================================ */
const TG = (typeof window !== 'undefined' && window.Telegram && window.Telegram.WebApp) || null;
let S = null; // the live game state (prediction in online mode)

const UIH = {}; // UI callbacks: onEco, onState, onChallenge, onGain, onStatus, onOffline, onRefAct, onDropped, onFatal

const NET = {
  mode: 'demo',
  cfg: null,
  token: '',
  me: null,
  eco: null,
  online: true,
  created: false,
  lastSync: 0,
  syncEvery: 12000,
  busy: false,
  sc: null,
  tp: { n: 0, mean: 0, m2: 0, last: 0 }, // statistics of ALL tap attempts since the last sync
  speed: 1, // demo only
  failures: 0,
  shopCache: null,
};

/* ---------- small helpers ---------- */
function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
function lsSet(k, v) { try { localStorage.setItem(k, v); return true; } catch (e) { return false; } }
function lsDel(k) { try { localStorage.removeItem(k); } catch (e) {} }
function muted(fn) {
  const h = hooks.emit;
  hooks.emit = function () {};
  try { return fn(); } finally { hooks.emit = h; }
}
function tgInitData() { return TG && TG.initData ? TG.initData : ''; }
function qs(name) {
  try { return new URLSearchParams(location.search).get(name); } catch (e) { return null; }
}
function fingerprint() {
  const parts = [];
  try {
    parts.push(screen.width + 'x' + screen.height + 'x' + (window.devicePixelRatio || 1));
    parts.push(Intl.DateTimeFormat().resolvedOptions().timeZone || '');
    parts.push(navigator.language || '', navigator.platform || '', navigator.hardwareConcurrency || 0, navigator.deviceMemory || 0);
    const cv = document.createElement('canvas');
    cv.width = 120; cv.height = 24;
    const c = cv.getContext('2d');
    c.textBaseline = 'top'; c.font = '14px Arial'; c.fillStyle = '#f60'; c.fillRect(0, 0, 60, 20);
    c.fillStyle = '#069'; c.fillText('OfficeDreams✓', 2, 2);
    const d = cv.toDataURL();
    let h = 0;
    for (let i = 0; i < d.length; i += 3) h = (h * 31 + d.charCodeAt(i)) | 0;
    parts.push(h);
  } catch (e) {}
  return parts.join('|').slice(0, 300);
}

/* ---------- HTTP ---------- */
async function http(method, path, body, opt) {
  opt = opt || {};
  const ctl = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timer = ctl ? setTimeout(() => ctl.abort(), opt.timeout || 15000) : null;
  try {
    const headers = { 'content-type': 'application/json' };
    if (NET.token) headers.authorization = 'Bearer ' + NET.token;
    const r = await fetch(path, {
      method, headers, body: body ? JSON.stringify(body) : undefined,
      signal: ctl ? ctl.signal : undefined, keepalive: !!opt.keepalive, credentials: 'omit', cache: 'no-store',
    });
    let j = null;
    try { j = await r.json(); } catch (e) { j = null; }
    return { status: r.status, j: j || { ok: false, err: 'json' } };
  } catch (e) {
    return { status: 0, j: { ok: false, err: 'net' } };
  } finally {
    if (timer) clearTimeout(timer);
  }
}
async function api(method, path, body) {
  let r = await http(method, path, body);
  if (r.status === 401 && path !== '/api/auth') {
    if (await reauth()) r = await http(method, path, body);
  }
  setOnline(r.status !== 0);
  if (r.j && r.j.eco) setEco(r.j.eco);
  return r.j;
}
function setOnline(v) {
  if (NET.online !== v) {
    NET.online = v;
    UIH.onStatus && UIH.onStatus(v);
  }
}
function setEco(eco) {
  if (!eco) return;
  NET.eco = eco;
  UIH.onEco && UIH.onEco(eco);
}

/* ---------- boot ---------- */
// returns {ok, mode} ; on fatal errors returns {ok:false, err}
async function netBoot(forceDemo) {
  if (forceDemo) return demoBoot();
  const cfgR = await http('GET', '/api/config', null, { timeout: 8000 });
  if (cfgR.status !== 200 || !cfgR.j || cfgR.j.v !== 2) {
    // no server next to the page: inside Telegram this is an outage, outside it is the demo
    if (tgInitData()) return { ok: false, err: 'server' };
    return demoBoot();
  }
  NET.cfg = cfgR.j;
  NET.mode = 'online';
  const a = await doAuth();
  if (!a.ok) return a;
  return { ok: true, mode: 'online' };
}
function authBody() {
  const body = { platform: (TG && TG.platform) || 'web', fp: fingerprint() };
  try { body.tz = Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch (e) {}
  try {
    const hp = new URLSearchParams((location.hash || '').replace(/^#/, ''));
    const sp = (TG && TG.initDataUnsafe && TG.initDataUnsafe.start_param) || qs('tgWebAppStartParam') || hp.get('tgWebAppStartParam') || qs('startapp') || qs('start') || '';
    if (sp) body.start = String(sp).slice(0, 64);
  } catch (e) {}
  const init = tgInitData();
  if (init) body.initData = init;
  else if (NET.cfg && NET.cfg.dev) {
    const id = parseInt(qs('dev') || lsGet('od2_dev') || '0', 10) || 0;
    if (id > 0) {
      lsSet('od2_dev', String(id));
      body.dev = { id, name: qs('name') || 'Dev ' + id, lang: qs('lang') || detectLang(), start: qs('start') || '' };
    }
  }
  return body;
}
async function doAuth() {
  const body = authBody();
  if (!body.initData && !body.dev) return { ok: false, err: 'no_telegram' };
  const r = await http('POST', '/api/auth', body);
  if (r.status === 0) return { ok: false, err: 'server' };
  if (!r.j || !r.j.ok || !r.j.token) return { ok: false, err: (r.j && r.j.err) || 'auth' };
  NET.token = r.j.token;
  NET.me = r.j.me;
  NET.created = !!r.j.created;
  const st = muted(() => deserialize(r.j.state));
  applyPrefs(st);
  if (NET.sc) {
    NET.sc.S = st;
    NET.sc.queue = [];
    NET.sc.inflight = null;
    NET.sc.lastTk = st.tk;
  } else NET.sc = createSyncClient(st, { applyAction, replay, stateHash, deserialize });
  NET.sc.seq = r.j.seq;
  S = NET.sc.S;
  NET.lastSync = Date.now();
  setEco(r.j.eco);
  if (r.j.challenge) UIH.onChallenge && UIH.onChallenge(r.j.challenge);
  return { ok: true };
}
async function reauth() {
  if (!tgInitData() && !(NET.cfg && NET.cfg.dev)) return false;
  const body = authBody();
  const r = await http('POST', '/api/auth', body);
  if (!r.j || !r.j.ok || !r.j.token) {
    if (r.status === 401 || r.status === 403) UIH.onFatal && UIH.onFatal('session');
    return false;
  }
  NET.token = r.j.token;
  // the server state may have moved on; adopt it
  muted(() => {
    const st = deserialize(r.j.state);
    applyPrefs(st);
    NET.sc.S = st;
    NET.sc.queue = [];
    NET.sc.inflight = null;
    NET.sc.lastTk = st.tk;
    NET.sc.seq = r.j.seq;
    S = st;
  });
  UIH.onState && UIH.onState(true);
  return true;
}

/* ---------- local preferences (not synced) ---------- */
function loadPrefs() {
  try { return JSON.parse(lsGet('od2_prefs') || '{}') || {}; } catch (e) { return {}; }
}
function savePrefs() {
  if (!S) return;
  const p = loadPrefs();
  p.sel = S.sel;
  p.settings = S.settings;
  lsSet('od2_prefs', JSON.stringify(p));
}
function applyPrefs(st) {
  const p = loadPrefs();
  if (p.sel && GENRES[p.sel.genre] && TIERS[p.sel.tier]) st.sel = { genre: p.sel.genre, tier: p.sel.tier, name: String(p.sel.name || '').slice(0, 22) || st.sel.name };
  if (p.settings && typeof p.settings === 'object') st.settings = Object.assign({ sound: true }, p.settings);
}

/* ---------- actions ---------- */
function netAct(type) {
  const args = Array.prototype.slice.call(arguments, 1);
  if (NET.mode === 'online') {
    const r = NET.sc.act.apply(null, [type].concat(args));
    S = NET.sc.S;
    if (r.ok && type !== 'tap') NET.syncSoon = true;
    return r;
  }
  const r = applyAction(S, [type].concat(args));
  if (r.ok) demoDirty();
  return r;
}
// tap attempt statistics (sent to the server, used for bot detection)
function noteTapAttempt(trusted) {
  const now = performance.now();
  const t = NET.tp;
  if (!trusted) t.untrusted = (t.untrusted || 0) + 1;
  if (t.last) {
    const dt = now - t.last;
    if (dt >= 15 && dt <= 5000) {
      t.n++;
      const d = dt - t.mean;
      t.mean += d / t.n;
      t.m2 += d * (dt - t.mean);
    }
  }
  t.last = now;
}

/* ---------- sync ---------- */
function netPump(nowMs) {
  if (NET.mode !== 'online') return demoPump(nowMs);
  if (NET.busy || !NET.sc) return;
  const since = nowMs - NET.lastSync;
  const backoff = NET.failures ? Math.min(60000, 3000 * Math.pow(2, NET.failures - 1)) : 0;
  if (since < backoff) return;
  const queued = NET.sc.queue.length;
  if ((NET.syncSoon && since > 2500) || (queued > 0 && since > 4000) || since > NET.syncEvery) netSync();
}
async function netSync(opt) {
  opt = opt || {};
  if (NET.mode !== 'online' || NET.busy || !NET.sc) return null;
  const body = NET.sc.prepare();
  if (!body) return null;
  NET.busy = true;
  NET.syncSoon = false;
  if (NET.tp.n > 0) body.tp = [NET.tp.n, Math.round(NET.tp.mean * 10) / 10, Math.round(NET.tp.m2)];
  const tpSaved = NET.tp;
  NET.tp = { n: 0, mean: 0, m2: 0, last: tpSaved.last };
  if (opt.full) body.full = true;
  const cashBefore = S.cash;
  const r = await http('POST', '/api/sync', body, { keepalive: !!opt.keepalive, timeout: 15000 });
  NET.busy = false;
  NET.lastSync = Date.now();
  if (r.status === 401) {
    NET.sc.fail();
    NET.tp = tpSaved;
    if (await reauth()) NET.syncSoon = true;
    return null;
  }
  if (r.status === 0 || r.status >= 500 || r.status === 429) {
    NET.sc.fail();
    NET.tp = tpSaved;
    NET.failures++;
    setOnline(r.status !== 0);
    return null;
  }
  setOnline(true);
  NET.failures = 0;
  const j = r.j;
  if (r.status !== 200) {
    // a malformed batch (should never happen): drop it and take the server state on the next sync
    NET.sc.inflight = null;
    NET.sc.queue = [];
    NET.syncSoon = true;
    return null;
  }
  let res;
  muted(() => { res = NET.sc.apply(j); });
  if (res.state) {
    S = NET.sc.S;
    UIH.onState && UIH.onState(true);
  }
  if (res.dropped && UIH.onDropped) UIH.onDropped(res.err);
  if (j.eco) setEco(j.eco);
  if (j.gain > 0 && UIH.onGain) UIH.onGain(j.gain);
  if (j.off > 60 && UIH.onOffline) UIH.onOffline(j.off, Math.max(0, S.cash - cashBefore));
  if (j.challenge && UIH.onChallenge) UIH.onChallenge(j.challenge);
  if (j.refAct && UIH.onRefAct) UIH.onRefAct();
  return j;
}
function netFlush() {
  if (NET.mode === 'online') netSync({ keepalive: true });
  else demoSave();
}

/* ---------- online API wrappers (demo fallbacks below) ---------- */
const netApi = {
  checkin: () => (NET.mode === 'online' ? syncThen(() => api('POST', '/api/checkin', {})) : demoCheckin()),
  tasks: () => (NET.mode === 'online' ? api('GET', '/api/tasks') : Promise.resolve({ ok: true, tasks: demoTasks() })),
  taskStart: (id) => api('POST', '/api/tasks/start', { id }),
  taskClaim: (id) => (NET.mode === 'online' ? syncThen(() => api('POST', '/api/tasks/claim', { id })) : demoTaskClaim(id)),
  friends: () => (NET.mode === 'online' ? api('GET', '/api/friends') : Promise.resolve({ ok: true, friends: [], l2: 0 })),
  leaderboard: () => (NET.mode === 'online' ? api('GET', '/api/leaderboard') : Promise.resolve(demoBoard())),
  shop: () => (NET.mode === 'online' ? api('GET', '/api/shop') : Promise.resolve({ ok: true, items: [], demo: true })),
  invoice: (sku) => api('POST', '/api/shop/invoice', { sku }),
  tonOrder: (sku) => api('POST', '/api/shop/ton', { sku }),
  donateInfo: () => (NET.mode === 'online' ? api('GET', '/api/donate') : Promise.resolve({ ok: true, demo: true, stars: 0, donors: 0, usd: 0, goalUsd: 5000, min: 100, max: 10000, mine: 0, top: [] })),
  donateInvoice: (stars) => api('POST', '/api/donate/invoice', { stars }),
  donateTon: (ton) => api('POST', '/api/donate/ton', { ton }),
  tonCheck: (id) => api('POST', '/api/shop/ton/check', { id }),
  challenge: (id, a, ts) => api('POST', '/api/challenge', { id, a, ts }),
  lang: (lang) => (NET.mode === 'online' ? api('POST', '/api/lang', { lang }) : Promise.resolve({ ok: true })),
  forget: () => api('POST', '/api/forget', { confirm: true }),
  wallet: (body) => api('POST', '/api/wallet', body),
  tonPayload: () => api('GET', '/api/ton/payload'),
  adClaim: () => api('POST', '/api/ads/claim', {}),
};
// make sure the server has seen our latest actions before a call that depends on the game state
async function syncThen(fn) {
  if (NET.sc && (NET.sc.queue.length || NET.busy)) {
    for (let i = 0; i < 20 && NET.busy; i++) await new Promise((r) => setTimeout(r, 150));
    await netSync();
  }
  return fn();
}

/* ---------- rewarded ads (Adsgram) ---------- */
let adCtl = null;
function loadMonetag(a) {
  return new Promise((res, rej) => {
    const fn = 'show_' + a.block;
    if (typeof window[fn] === 'function') return res(window[fn]);
    const s = document.createElement('script');
    s.src = a.sdk;
    s.async = true;
    s.setAttribute('data-zone', String(a.block));
    s.setAttribute('data-sdk', fn);
    s.onload = () => setTimeout(() => (typeof window[fn] === 'function' ? res(window[fn]) : rej(new Error('sdk'))), 50);
    s.onerror = () => rej(new Error('load'));
    document.head.appendChild(s);
  });
}
async function showAd() {
  const a = NET.mode === 'online' && NET.cfg && NET.cfg.ads;
  if (!a) return { ok: false, err: 'ads_off' };
  try {
    if (a.provider === 'monetag') {
      const show = await loadMonetag(a);
      const ymid = (NET.me ? NET.me.id : 0) + '-' + Date.now().toString(36);
      // resolves when the rewarded interstitial is closed (after Monetag confirms the view when postbacks are on)
      await show({ type: 'end', ymid, requestVar: 'reward_btn' });
    } else {
      if (!window.Adsgram) await loadScript('https://sad.adsgram.ai/js/sad.min.js');
      if (!adCtl) adCtl = window.Adsgram.init({ blockId: String(a.block) });
      const res = await adCtl.show();
      if (!res || !res.done) return { ok: false, err: 'ads_skip' };
    }
  } catch (e) {
    return { ok: false, err: e && e.done === false && !e.error ? 'ads_skip' : 'ads_fail' };
  }
  if (a.callback) {
    // the ad network tells our server directly; give it a moment, then pull the new state
    await new Promise((r) => setTimeout(r, 2500));
    await netSync({ full: true });
    return { ok: true, callback: true };
  }
  const r = await netApi.adClaim();
  if (r && r.ok) NET.syncSoon = true;
  return r || { ok: false, err: 'net' };
}

/* ---------- TON Connect ---------- */
let tonUI = null;
function loadScript(src) {
  return new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = src;
    s.async = true;
    s.onload = () => res();
    s.onerror = () => rej(new Error('load'));
    document.head.appendChild(s);
  });
}
async function tonConnect() {
  if (NET.mode !== 'online' || !NET.cfg) return { ok: false, err: 'demo' };
  const tc = NET.cfg.tonconnect || {};
  if (!tc.manifest) return { ok: false, err: 'not_configured' };
  try {
    await ensureTonUI();
    if (tonUI.connected) { try { await tonUI.disconnect(); } catch (e) {} }
    const pl = await netApi.tonPayload();
    if (pl && pl.payload) tonUI.setConnectRequestParameters({ state: 'ready', value: { tonProof: pl.payload } });
    await tonUI.openModal();
    return { ok: true };
  } catch (e) {
    return { ok: false, err: 'tonconnect' };
  }
}
// one TON Connect instance; restores a wallet connected earlier
async function ensureTonUI() {
  const tc = (NET.cfg && NET.cfg.tonconnect) || {};
  if (!tc.manifest) throw new Error('not_configured');
  if (!window.TON_CONNECT_UI) await loadScript(tc.cdn);
  if (!tonUI) {
    const opts = { manifestUrl: tc.manifest };
    if (NET.cfg.bot && NET.cfg.app) opts.actionsConfiguration = { twaReturnUrl: 'https://t.me/' + NET.cfg.bot + '/' + NET.cfg.app };
    tonUI = new window.TON_CONNECT_UI.TonConnectUI(opts);
    tonUI.onStatusChange(async (w) => {
      if (!w || !w.account) return;
      const tp = w.connectItems && w.connectItems.tonProof;
      const body = tp && tp.proof ? { account: w.account, proof: tp.proof } : { address: w.account.address };
      const r = await netApi.wallet(body);
      UIH.onWallet && UIH.onWallet(r);
    });
  }
  try { await Promise.race([tonUI.connectionRestored, new Promise((r) => setTimeout(r, 3000))]); } catch (e) {}
  return tonUI;
}
// a text comment as a one-cell BOC (op 0 + utf8), base64 — what wallets show as "comment"
function commentBoc(text) {
  const t = new TextEncoder().encode(text);
  const data = new Uint8Array(4 + t.length);
  data.set(t, 4);
  const cell = new Uint8Array(2 + data.length);
  cell[0] = 0; cell[1] = data.length * 2;
  cell.set(data, 2);
  const head = [0xb5, 0xee, 0x9c, 0x72, 0x01, 0x01, 0x01, 0x01, 0x00, cell.length, 0x00];
  const out = new Uint8Array(head.length + cell.length);
  out.set(head, 0); out.set(cell, head.length);
  let bin = '';
  out.forEach((b) => { bin += String.fromCharCode(b); });
  return btoa(bin);
}
// pay a shop order with TON through the connected wallet
async function tonPay(order) {
  let ui;
  try { ui = await ensureTonUI(); } catch (e) { return { ok: false, err: 'tonconnect' }; }
  if (!ui.connected) return { ok: false, err: 'need_wallet' };
  try {
    await ui.sendTransaction({ validUntil: order.validUntil, messages: [{ address: order.to, amount: String(order.amount), payload: commentBoc(order.comment) }] });
    return { ok: true };
  } catch (e) {
    return { ok: false, err: 'ton_rejected' };
  }
}
async function tonDisconnect() {
  try { if (tonUI && tonUI.connected) await tonUI.disconnect(); } catch (e) {}
  return netApi.wallet({ disconnect: true });
}

/* ============================================================
   DEMO backend (local only)
   ============================================================ */
const DEMO_KEY = 'od2_demo';
const DEMO = { eco: null, act: null, dirtyAt: 0, savedAt: 0, lastPts: 0 };
const dayKeyNow = () => Math.floor(Date.now() / 86400000);
function demoBoot() {
  NET.mode = 'demo';
  NET.cfg = {
    v: 2, demo: true, season: { id: 0, name: 'Season 0' }, symbol: 'OFFICE', links: {},
    eco: { D: 300, l1: 0.1, l2: 0.02, cashEach: 500, gemsEach: 2, pass: 1.25 }, checkin: [10, 12, 15, 18, 22, 28, 40], shop: [],
  };
  let saved = null;
  try { saved = JSON.parse(lsGet(DEMO_KEY) || 'null'); } catch (e) { saved = null; }
  let st = null;
  if (saved && saved.state) { try { st = muted(() => deserialize(saved.state)); } catch (e) { st = null; } }
  NET.created = !st;
  if (!st) st = newState();
  applyPrefs(st);
  S = st;
  const dk = dayKeyNow();
  syncDay(S, dk);
  DEMO.eco = Object.assign({ pts: 0, day: dk, raw: 0, eff: 0, streak: 0, last: 0, days: 0, lastPlay: 0, bySrc: {}, tasks: {} }, (saved && saved.eco) || {});
  DEMO.act = (saved && saved.act) || demoVector(S);
  NET.speed = (saved && saved.speed) || 1;
  // offline income while the tab was closed
  if (saved && saved.at) {
    const away = (Date.now() - saved.at) / 1000;
    if (away > 30) {
      const before = S.cash;
      const sec = muted(() => advanceOffline(S, away));
      if (sec > 60) setTimeout(() => UIH.onOffline && UIH.onOffline(sec, Math.max(0, S.cash - before)), 900);
    }
  }
  demoRoll();
  setEco(demoView());
  return { ok: true, mode: 'demo' };
}
function demoVector(st) {
  const t = st.stats;
  return { taps: t.taps, relW: t.relW, ups: t.ups, evs: t.evs, flrW: t.flrW, goalW: t.goalW, dqW: t.dqW };
}
function demoRoll() {
  const e = DEMO.eco, dk = dayKeyNow();
  if (e.day !== dk) { e.day = dk; e.raw = 0; e.eff = 0; e.bySrc = {}; }
}
function demoCredit(kind, raw, fixed) {
  demoRoll();
  const e = DEMO.eco, D = 300;
  let eff = raw;
  if (!fixed) {
    const prev = e.raw, next = prev + raw;
    eff = D * (1 - Math.exp(-next / D)) - D * (1 - Math.exp(-prev / D));
    e.raw = next;
    e.eff += eff;
  }
  e.pts = Math.round((e.pts + eff) * 1000) / 1000;
  e.bySrc[kind] = Math.round(((e.bySrc[kind] || 0) + eff) * 10) / 10;
  return Math.round(eff * 10) / 10;
}
function demoView() {
  const e = DEMO.eco, dk = dayKeyNow(), D = 300;
  const alive = e.last === dk || e.last === dk - 1;
  const streak = alive ? e.streak : 0;
  const nextStreak = e.last === dk ? streak : streak + 1;
  const table = NET.cfg.checkin;
  let today = 0;
  for (const k in e.bySrc) today += e.bySrc[k];
  return {
    pts: Math.round(e.pts * 10) / 10, today: Math.round(today * 10) / 10, bySrc: e.bySrc, D, used: Math.min(1, e.eff / D), pass: 0,
    checkin: { done: e.last === dk, streak, next: table[(Math.max(1, nextStreak) - 1) % 7], table },
    ref: { code: '', link: '', total: 0, active: 0, pts: 0, l1: 0.1, l2: 0.02, cashEach: 500, gemsEach: 2 },
    risk: 0, wallet: null, days: e.days, mult: 1, demo: true,
  };
}
function demoDirty() { DEMO.dirtyAt = Date.now(); }
function demoPump(nowMs) {
  if (nowMs - (DEMO.tickAt || 0) < 4000) return;
  DEMO.tickAt = nowMs;
  const dk = dayKeyNow();
  syncDay(S, dk);
  const cur = demoVector(S), prev = DEMO.act || {};
  const W = PTS_W;
  const d = (k) => Math.max(0, (cur[k] || 0) - (prev[k] || 0));
  const raw = d('taps') * W.tap + d('relW') * W.rel + d('ups') * W.up + d('evs') * W.ev + d('flrW') + d('goalW') + d('dqW');
  DEMO.act = cur;
  if (raw > 0) {
    const e = DEMO.eco;
    if (e.lastPlay !== dk) { e.days++; e.lastPlay = dk; }
    const g = demoCredit('play', raw, false);
    if (g > 0 && UIH.onGain) UIH.onGain(g);
    setEco(demoView());
  }
  if (nowMs - DEMO.savedAt > 10000 || DEMO.dirtyAt > DEMO.savedAt) demoSave();
}
function demoSave() {
  if (!S) return;
  DEMO.savedAt = Date.now();
  const o = JSON.parse(serialize(S));
  lsSet(DEMO_KEY, JSON.stringify({ state: o, eco: DEMO.eco, act: DEMO.act, speed: NET.speed, at: Date.now() }));
}
function demoReset() {
  lsDel(DEMO_KEY);
  DEMO.eco = null;
  demoBoot();
  NET.created = true;
}
function demoCheckin() {
  const e = DEMO.eco, dk = dayKeyNow();
  if (e.last === dk) return Promise.resolve({ ok: false, err: 'done', eco: demoView() });
  if (S.stats.taps < 20) return Promise.resolve({ ok: false, err: 'play_first', eco: demoView() });
  const streak = e.last === dk - 1 ? e.streak + 1 : 1;
  e.streak = streak;
  e.last = dk;
  const pts = demoCredit('checkin', NET.cfg.checkin[(streak - 1) % 7], true);
  demoSave();
  const eco = demoView();
  setEco(eco);
  return Promise.resolve({ ok: true, pts, streak, eco });
}
function demoTasks() {
  const e = DEMO.eco;
  const t = (id, pts, cur, max) => ({ id, pts, kind: 'auto', cur: Math.min(cur, max), max, done: !!e.tasks[id], claimable: !e.tasks[id] && cur >= max });
  return [
    t('play3', 25, e.days, 3),
    t('streak3', 15, e.streak, 3),
    t('streak7', 50, e.streak, 7),
    { id: 'wallet', pts: 30, kind: 'tg', done: false, claimable: false },
    { id: 'invite1', pts: 20, kind: 'tg', done: false, claimable: false },
  ];
}
function demoTaskClaim(id) {
  const t = demoTasks().find((x) => x.id === id);
  if (!t || !t.claimable) return Promise.resolve({ ok: false, err: 'notyet', tasks: demoTasks() });
  DEMO.eco.tasks[id] = 1;
  const pts = demoCredit('task', t.pts, true);
  demoSave();
  setEco(demoView());
  return Promise.resolve({ ok: true, pts, tasks: demoTasks(), eco: demoView() });
}
function demoBoard() {
  const e = DEMO.eco;
  return { ok: true, top: e.pts > 0 ? [{ rank: 1, name: L('demo.you'), me: true, pts: Math.round(e.pts * 10) / 10 }] : [], me: e.pts > 0 ? { rank: 1, pts: Math.round(e.pts * 10) / 10 } : null, total: e.pts > 0 ? 1 : 0, demo: true };
}
