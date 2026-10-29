/* «Молчи до шести» (Hush Till Six) — движок интерактивного кино: кадры с движением камеры и эффектами, голос, субтитры, выборы, мини-игры.
   Звук: живой фон места (никогда не цифровая тишина), случайные далёкие звуки, тишина перед каждым скримером,
   «подпись» Жильца перед появлением, стук Кати по стеклу в такт её руке, слой обертонов для динамика телефона.
   Поля кадра (story.js) сверх старых: sms {from, text, at}, fx 'glitch', subl (кадр-вспышка лица), props, cctv. */
(() => {
'use strict';
const S = window.STORY, $ = id => document.getElementById(id);
const tr = window.tr || (s => s), trf = window.trf || ((s, v) => s.replace(/\{(\w+)\}/g, (m, k) => v[k] ?? m)), LNG = window.NS_LANG || 'ru', EN = LNG !== 'ru';
const DESK = window.nsDesktop || null;   // ПК-версия (tools/desktop: окно Electron): выход из игры, полный экран окна, без доната и спонсоров из интернета
const VL = window.NS_VOICE || (EN ? 'en' : 'ru');   // чьими записями озвучен язык игры (папка snd/<VL>/; русский — snd/)
const OVERS0 = S.overs.slice(), OVER0 = {};   // названия смертей по-русски — ключи для счётчика «Смертей найдено» (переведённые терялись при смене языка, а в английской две смерти звались одинаково)
for (const id in S.scenes) { const th = S.scenes[id].then; if (th && th.over) OVER0[id] = th.over; }
if (window.trStory) window.trStory(S);
if (EN) {   // статичные надписи страницы
  document.documentElement.lang = LNG;
  for (const b of document.querySelectorAll('[title]')) { b.title = tr(b.title); b.setAttribute('aria-label', b.title); }
  const kk = document.getElementById('keys'); if (kk) for (const d of kk.children) { const kb = d.querySelector('kbd'); if (kb) kb.textContent = tr(kb.textContent);   // и сама клавиша («мышь» оставалась по-русски)
    if (d.lastChild && d.lastChild.nodeType === 3) d.lastChild.textContent = tr(d.lastChild.textContent); }
}
const cv = $('cv'), g = cv.getContext('2d');
let W = 1080, H = 1920, US = 1, CS = (() => { const q = /[?&]cs=([\d.]+)/.exec(location.search); return q ? Math.max(.25, Math.min(1, +q[1])) : 1; })();   // ?cs=0.5 — меньший холст (автопроверка кадров); холст движка: 1080×1920 вертикально или 1920×1080 горизонтально; US — масштаб надписей на холсте; CS — масштаб холста (слабый телефон — 2/3)
const dlgEl = $('dlg'), tapEl = $('tap'), chapEl = $('chap'), clockEl = $('clock'), noteEl = $('note'), ruleEl = $('rule'), choiceEl = $('choice'), uiEl = $('ui'), mgEl = $('mg'), smsEl = $('sms'), actEl = $('act');
const LS = { get(k, d) { try { const v = localStorage.getItem('ns1_' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
             set(k, v) { try { localStorage.setItem('ns1_' + k, JSON.stringify(v)); } catch {} } };
const calm = () => !!LS.get('calm', false);   // настройка «меньше вспышек»
// экран: горизонтальный (16:9) или вертикальный (9:16). На ПК по умолчанию горизонтальный, на телефоне — вертикальный; меняется в настройках
const FINE = matchMedia('(pointer: fine)').matches;
let LAND = LS.get('land3', true);   // v3: кадры поставлены под 16:9 — по умолчанию горизонтально (в настройках можно вертикально)
// точка, заданная в долях вертикального кадра (сценарий писался под 9:16), → доли текущего кадра (угол обзора по вертикали тот же)
const scrX = x => LAND ? .5 + (x - .5) * (9 / 16) / (16 / 9) : x;
const voiceOn = () => LS.get('voice', true);   // озвучка реплик (субтитры остаются всегда)
const VOL = Object.assign({ all: 1, mus: 1, voice: 1, sfx: 1, amb: 1 }, LS.get('vol', {}));   // ползунки громкости в настройках (amb — дождь и фон места)
let uMus = null, uSfx = null;
const escH = v => String(v).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
// значки интерфейса: простые векторные, без эмодзи
const ICON = {
  phones: '<svg class="ic" viewBox="0 0 24 24"><path d="M4 16v-4a8 8 0 0 1 16 0v4"/><path class="f" d="M3 14h4v7H3zM17 14h4v7h-4z"/></svg>',
  flash: '<svg class="ic" viewBox="0 0 24 24"><path class="f" d="M13 2L5 14h6l-1 8 8-12h-6z"/></svg>',
  star: '<svg class="ic" viewBox="0 0 24 24"><path class="f" d="M12 2.5l2.9 6.1 6.6.8-4.9 4.6 1.3 6.6L12 17.3l-5.9 3.3 1.3-6.6-4.9-4.6 6.6-.8z"/></svg>',
  play: '<svg class="ic" viewBox="0 0 24 24"><path class="f" d="M7 5l11 7-11 7z"/></svg>',
  left: '<svg class="ic" viewBox="0 0 24 24"><path d="M15 4l-8 8 8 8"/></svg>',
  right: '<svg class="ic" viewBox="0 0 24 24"><path d="M9 4l8 8-8 8"/></svg>',
  plug: '<svg class="ic" viewBox="0 0 24 24"><path d="M9 2v5M15 2v5"/><path class="f" d="M5 7h14v4a7 7 0 0 1-14 0z"/><path d="M12 18v5"/></svg>',
  msg: '<svg viewBox="0 0 24 24"><path class="f" d="M3 5h18v12H9l-6 4z"/></svg>',
  x: '<svg class="ic" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  ok: '<svg class="ic" viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7"/></svg>',
  snd: '<svg class="ic" viewBox="0 0 24 24"><path class="f" d="M3 9h4l5-4v14l-5-4H3z"/><path class="on1" d="M16 8.5a5 5 0 0 1 0 7M18.8 6a8.5 8.5 0 0 1 0 12"/><path class="off" d="M16 9l5 6M21 9l-5 6"/></svg>'
};

/* ---------- загрузка ---------- */
const BUF = {}; let TIM = {};
const SNDS = ['theme', 'drone', 'tension', 'boom', 'scare', 'knock', 'ding', 'heart', 'rain', 'static', 'buzz', 'ring', 'ring_mob', 'tick', 'click', 'door', 'steps',
  'ch_katya', 'ch_whisper', ...Array.from({ length: 15 }, (_, i) => 'p' + String(i + 1).padStart(2, '0'))];   // реплики пролога — сразу, остальные в фоне
// звуковые эффекты (ElevenLabs): докачиваются в фоне после старта — список плюс всё, что звучит в сценах и в смене
const storySfx = () => { const z = new Set(SFX2);
  for (const sc of Object.values(S.scenes)) { for (const b of sc.beats || []) for (const x of b.sfx || []) z.add(x[0]); for (const x of (sc.then || {}).sfx || []) z.add(x[0]); }
  for (const a of Object.values((S.shift || {}).an || {})) for (const x of [a.snd, a.sfx]) if (x) z.add(x[0]);
  ['bolt', 'pen', 'rain_burst', 'shift', 'end_dawn', 'end_newentry', 'end_inside', 'end_sister'].forEach(k => z.add(k)); z.delete('voice'); return [...z]; };   // + своя музыка (tools/music/compose.py)
const SFX2 = ['hush', 'skin_stretch', 'room_tone', 'drone_dread', 'sub_presence', 'breath_close', 'neck_crack', 'steps_barefoot', 'eat_wet', 'whispers', 'metal_groan', 'glass_tap', 'door_slams', 'reverse_swell', 'fluor_flicker', 'heart_held', 'hum_stop', 'phone_crackle', 'lift_ding_far', 'tinnitus', 'jumpscare', 'glass_slam', 'cleaner_cry_a', 'cleaner_cry_b', 'mop_swish'];
let voiceOut = null, voiceRev = null, directG = null, AC = null, revIn = null, muffle = null, radioBus = null, phoneBus = null, master, outG = null, musicG, voiceG, sfxG, uAmb = null, sfxRev = null, sfxLow = null, lowSend = null, musicSend = null, rawSnd = {}, analyser = null, abuf = null, talkV = 0;
// громкость голоса сейчас (для движения рта)
function voiceLevel() { if (!analyser) return 0; analyser.getByteTimeDomainData(abuf); let q = 0; for (let i = 0; i < abuf.length; i += 2) { const v = (abuf[i] - 128) / 128; q += v * v; } return Math.min(1, Math.sqrt(q / (abuf.length / 2)) * 6); }
// XHR вместо fetch: так файлы читаются и внутри Android-приложения (file://)
function get(url, type) { return new Promise((ok, no) => { const x = new XMLHttpRequest(); x.open('GET', url); x.responseType = type; x.onload = () => (x.status === 200 || x.status === 0) && x.response ? ok(x.response) : no(); x.onerror = no; x.send(); }); }
let loadP = 0;
// реплики (их ключи — в timing.json) в английской версии лежат в snd/en/
const VOICE_RE = /^ch_(katya|whisper)$/;
const SNDV = { sv08: 5, n26: 5 };   // перезаписанные реплики — своя версия файла (остальные звуки из кэша не перекачиваются)
const sndUrl = k => (VL !== 'ru' && (VOICE_RE.test(k) || TIM[k]) ? 'snd/' + VL + '/' : 'snd/') + k + '.mp3?v=' + (SNDV[k] || 4);
async function load(onp) {
  let done = 0; const total = SNDS.length + 2 + 7 * 4, tick = (n = 1) => onp(Math.min(1, (done += n) / total));   // модели тяжёлые: каждая считается за 4
  // 3D-мир строится кодом прямо здесь; заодно прогреваем шейдеры, чтобы первая сцена не дёргалась
  const models = window.__W3chars && window.__W3chars.load ? window.__W3chars.load(() => tick(4)).catch(() => false) : Promise.resolve(false);
  const world = Promise.all([models, Promise.resolve(window.NS_FONTS).catch(() => null)]).then(() => new Promise(r => setTimeout(() => { try { if (window.World && World.init()) { World.vhs = VHS_LV[LS.get('vhs', vhsDef())] ?? .7; World.warm(true); } } catch (e) { console.error(e); } worldTried = true; tick(); r(); }, 30)));   // иероглифы (NSCJK) — до текстур с надписями
  // версии timing_<язык>.json — поднимать при каждой новой нарезке/сборке (кэш браузера)
  const TIMV = { en: 13, uk: 6, es: 2, pt: 2, de: 2, fr: 3, it: 3, pl: 3, tr: 3, zh: 3, ja: 3, ko: 3 };
  // сначала тайминги: по ним sndUrl решает, где лежит реплика (английские — в snd/en/); иначе пролог грузился русским
  const tm = get(EN ? 'timing_' + LNG + '.json?v=' + (TIMV[LNG] || 1) : 'timing.json?v=17', 'json').then(j => { TIM = j || {}; tick(); }).catch(tick);
  const sn = SNDS.map(k => tm.then(() => get(sndUrl(k), 'arraybuffer')).then(b => { rawSnd[k] = b; tick(); }).catch(tick));
  await Promise.all([world, ...sn, tm]);
  // остальные реплики (их много) докачиваются в фоне, уже во время игры — очередью по 4, в порядке сцен; нужные сейчас встают вперёд (voiceReady)
  const order = [...storySfx()]; for (const sc of Object.values(S.scenes)) order.push(...sceneKeys(sc)); order.push(...Object.keys(TIM));
  for (const k of order) if (!SNDS.includes(k)) fetchSnd(k);
}
// Реплики декодируются по мере надобности: все 273 сразу — это ~210 МБ памяти, телефон от этого тормозит и вылетает.
// Нужны реплики текущей сцены и следующих за ней (чтобы склейка не ждала); старые отпускаются, держим не больше VOICE_KEEP
const voiceWant = new Set(), voiceOrder = [], sndJob = {}, sndQ = []; let sndBusy = 0, voiceMiss = 0;   // voiceMiss — реплика должна была прозвучать, а буфера не было (проверяют тесты)
const VOICE_KEEP = 32, isVoice = k => !!TIM[k], LAZY_RE = /^(shift|end_)/;   // большая музыка (смена 96 с, финалы) тоже декодируется по надобности
const sceneKeys = sc => { const ks = []; for (const b of (sc && sc.beats) || []) for (const k of b.say || []) ks.push(k); return ks; };
const objKeys = o => { const ks = new Set(); JSON.stringify(o, (k, v) => { if (typeof v === 'string' && isVoice(v)) ks.add(v); return v; }); return [...ks]; };   // все ключи реплик внутри объекта (события смены, странности, звонки)
const decQ = {};   // идущие декодирования реплик — чтобы не декодировать одну дважды
const decodeV = k => decQ[k] || (decQ[k] = decodeOne(k, rawSnd[k]).then(() => { voiceOrder.push(k); delete decQ[k]; }));
function fetchSnd(k, urgent) {
  let j = sndJob[k];
  if (!j) { j = sndJob[k] = { k, queued: true }; j.done = new Promise(r => (j.res = r)); }
  if (j.queued) { const i = sndQ.indexOf(j); if (i < 0) urgent ? sndQ.unshift(j) : sndQ.push(j); else if (urgent && i > 0) { sndQ.splice(i, 1); sndQ.unshift(j); } }
  sndPump(); return j.done;
}
function sndPump() {
  while (sndBusy < 4 && sndQ.length) { const j = sndQ.shift(); j.queued = false; sndBusy++;
    get(sndUrl(j.k), 'arraybuffer').then(b => { rawSnd[j.k] = b; if (AC && !isVoice(j.k) && !LAZY_RE.test(j.k)) return decodeOne(j.k, b); if (AC && voiceWant.has(j.k)) return decodeV(j.k); })
      .catch(() => {}).then(() => { sndBusy--; j.res(); sndPump(); }); }
}
function voiceReady(keys) {   // декодировать реплики (докачать, если ещё нет); обещание — когда все готовы
  const ps = [];
  for (const k of keys) { if (!isVoice(k)) continue; voiceWant.add(k); if (BUF[k] || !AC) continue;
    ps.push(rawSnd[k] ? decodeV(k) : fetchSnd(k, true)); }
  return Promise.all(ps).then(voiceTrim);
}
function voiceTrim() {   // старые реплики, которые сейчас не нужны, отпускаем
  const old = voiceOrder.filter(k => !voiceWant.has(k));
  while (voiceOrder.length > VOICE_KEEP && old.length) { const k = old.shift(); voiceOrder.splice(voiceOrder.indexOf(k), 1); delete BUF[k]; }
}
function voicePrep(id) {   // реплики этой сцены — сейчас; тех, куда она ведёт, — следом
  const sc = S.scenes[id]; voiceWant.clear(); const ids = new Set();
  JSON.stringify((sc && sc.then) || {}, (k, v) => { if (typeof v === 'string' && S.scenes[v]) ids.add(v); return v; });
  const p = voiceReady(sceneKeys(sc)); p.then(() => voiceReady([...ids].flatMap(n => sceneKeys(S.scenes[n])))); return p;
}
// голос в пространстве: рассказ героя — сухой и близкий; остальные звучат в помещении.
// Метка реплики (fx в timing.json): рация и телефон — узкая полоса с хрипом; за стеклом и за дверью — глухо;
// «чужой» голос (оно говорит голосом близкого) — чище и на полтона ниже, с низким призраком под ним; шёпот рядом — сухо, без эха
function voiceFx(k) {
  if (!AC || !voiceRev) return [{}];
  const who = whoOf(k), m = TIM[k] || {}, fx = m.fx || '', aloud = (m.t || '').trim().startsWith('—');
  if (fx === 'radio') return [{ to: [[radioBus, 1.35], [voiceRev, .06]] }];
  if (fx === 'phone') return [{ to: [[phoneBus, 1.25]] }];
  if (fx === 'phonefake') return [{ rate: .96, to: [[phoneBus, 1.25]] }, { rate: .5, gain: .14, dt: .02, cut: durOf(k), to: [[phoneBus, 1]] }];   // в трубке твой же голос, только не твой
  if (fx === 'glass') return [{ to: [[muffle, 1.4], [voiceRev, .2]] }];
  if (fx === 'muffle') return [{ to: [[muffle, 1.5], [voiceRev, .35]] }];
  if (fx === 'near') return [{ to: [[voiceG, 1.05]] }];
  if (fx === 'fake') return [{ rate: .95, to: [[voiceG, 1], [voiceRev, .05]] }, { rate: .5, gain: .1, dt: .03, cut: durOf(k), to: [[voiceG, 1]] }];
  if (who === 'ИТАН') return [aloud ? { to: [[voiceG, 1], [voiceRev, .12]] } : {}];
  if (who === '???') return [{ rate: .97, to: [[voiceG, 1], [voiceRev, .3]] }, { rate: .5, gain: .16, cut: durOf(k), to: [[voiceG, 1], [voiceRev, .3]] }];
  return [{ to: [[voiceG, 1], [voiceRev, .2]] }];
}
function decodeOne(k, b) { return new Promise(r => { try { const pr = AC.decodeAudioData(b.slice(0), d => { BUF[k] = d; r(); }, () => r()); if (pr && pr.catch) pr.catch(() => r()); } catch { r(); } }); }
let decoding = null;
const filt = (type, f, q) => { const n = AC.createBiquadFilter(); n.type = type; n.frequency.value = f; if (q != null) n.Q.value = q; return n; };
function initAudio() {   // контекст создаём заранее (он «спит» до нажатия), звуки раскодируем в фоне
  if (decoding) return decoding;
  const Ctx = window.AudioContext || window.webkitAudioContext; if (!Ctx) return (decoding = Promise.resolve());
  AC = new Ctx();
  const muted = LS.get('mute', false);
  // сумма всех шин → компрессор → общая громкость игрока → выход. Ползунок «Громкость» стоит после компрессора:
  // до него компрессор возвращал часть убавленного, и ползунок почти не менял громкость
  master = AC.createGain(); const comp = AC.createDynamicsCompressor(); comp.threshold.value = -16; comp.ratio.value = 4; comp.attack.value = .004; comp.release.value = .25;
  outG = AC.createGain(); outG.gain.value = muted ? 0 : VOL.all; master.connect(comp); comp.connect(outG); outG.connect(AC.destination);
  // крик скримера — мимо компрессора, чтобы был громче голоса; резкие 4–5 кГц чуть приглушены (динамик телефона их режет в уши). Это звук — слушается ползунка «Звуки»
  directG = AC.createGain(); directG.gain.value = VOL.sfx; { const pk = filt('peaking', 4500, 1.2); pk.gain.value = -4; directG.connect(pk); pk.connect(outG); }
  musicG = AC.createGain(); musicG.gain.value = .55; uMus = AC.createGain(); uMus.gain.value = VOL.mus; musicG.connect(uMus); uMus.connect(master);
  voiceG = AC.createGain(); voiceG.gain.value = 1; voiceOut = AC.createGain(); voiceOut.gain.value = voiceOn() ? VOL.voice : 0; voiceG.connect(voiceOut); voiceOut.connect(master);
  sfxG = AC.createGain(); sfxG.gain.value = .8; uSfx = AC.createGain(); uSfx.gain.value = VOL.sfx; uSfx.connect(master); sfxG.connect(uSfx);
  uAmb = AC.createGain(); uAmb.gain.value = VOL.amb; uAmb.connect(master);   // дождь и фон места — свой ползунок
  // слой для динамика телефона: он не играет ниже ~250 Гц, поэтому гул, дрон и сердце получают мягкие обертоны 200–1400 Гц
  { lowSend = AC.createGain(); const lp = filt('lowpass', 260, .7), dr = AC.createGain(), ws = AC.createWaveShaper(), hp = filt('highpass', 200, .7), lp2 = filt('lowpass', 1400, .7), wet = AC.createGain();
    const n = 1024, cu = new Float32Array(n); for (let i = 0; i < n; i++) { const x = i / (n - 1) * 2 - 1; cu[i] = Math.tanh(x * 3) / Math.tanh(3); }
    ws.curve = cu; ws.oversample = 'none'; dr.gain.value = 2; wet.gain.value = .6;
    lowSend.connect(lp); lp.connect(dr); dr.connect(ws); ws.connect(hp); hp.connect(lp2); lp2.connect(wet); wet.connect(master);
    musicSend = AC.createGain(); musicSend.gain.value = 1; uMus.connect(musicSend); musicSend.connect(lowSend);
    sfxLow = AC.createGain(); sfxLow.gain.value = VOL.sfx; sfxLow.connect(lowSend); }   // низ звуков — через ползунок «Звуки», как и сами звуки
  // пространство: реверберация (импульс — шум с затуханием) и «за стеклом/дверью» — глухой фильтр
  { const len = AC.sampleRate * 2.4 | 0, ir = AC.createBuffer(2, len, AC.sampleRate);
    for (let c = 0; c < 2; c++) { const d = ir.getChannelData(c); for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3.2) * (i < 90 ? i / 90 : 1); }
    revIn = AC.createConvolver(); revIn.buffer = ir; const rg = AC.createGain(); rg.gain.value = .55; revIn.connect(rg); rg.connect(master);
    voiceRev = AC.createGain(); voiceRev.gain.value = voiceOn() ? VOL.voice : 0; voiceRev.connect(revIn);   // эхо голосов — через свой кран: выключили озвучку — нет и эха
    sfxRev = AC.createGain(); sfxRev.gain.value = VOL.sfx; sfxRev.connect(revIn); }   // эхо звуков — через ползунок «Звуки»
  muffle = filt('lowpass', 1100, .7); muffle.connect(voiceG);
  // рация: узкая полоса + лёгкий перегруз; телефон: узкая полоса, чище
  { const h = filt('highpass', 480, .8), l = filt('lowpass', 2600, .9), ws = AC.createWaveShaper(), cu = new Float32Array(256);
    for (let i = 0; i < 256; i++) { const x = i / 128 - 1; cu[i] = Math.tanh(x * 2.6) / Math.tanh(2.6); }
    ws.curve = cu; h.connect(ws); ws.connect(l); l.connect(voiceG); radioBus = h;
    const h2 = filt('highpass', 330, .7), l2 = filt('lowpass', 3300, .7); h2.connect(l2); l2.connect(voiceG); phoneBus = h2; }
  analyser = AC.createAnalyser(); analyser.fftSize = 512; voiceG.connect(analyser); abuf = new Uint8Array(analyser.fftSize);
  bedInit();
  return (decoding = Promise.all(Object.entries(rawSnd).filter(([k]) => SNDS.includes(k)).map(([k, b]) => decodeOne(k, b))).then(() => {
    for (const [k, b] of Object.entries(rawSnd)) if (!BUF[k] && !isVoice(k) && !LAZY_RE.test(k)) decodeOne(k, b);   // фоновые звуки — не задерживают старт; реплики — по сценам (voiceReady), большая музыка — в music()
  }));
}
function unlockAudio() {   // только внутри нажатия: иначе телефон (особенно iPhone) не даст включить звук
  if (!AC) return;
  try { AC.resume(); const b = AC.createBuffer(1, 1, 22050), s = AC.createBufferSource(); s.buffer = b; s.connect(AC.destination); s.start(0); } catch {}
}
// кнопка «во весь экран» в углу: видна, когда браузер умеет полный экран, а игра сейчас не в нём (вышли жестом «назад» или Esc)
function fullBtn() { const b = $('bFull'), d = document; if (!b) return; const can = d.fullscreenEnabled || d.webkitFullscreenEnabled, on = d.fullscreenElement || d.webkitFullscreenElement; b.classList.toggle('hide', !!DESK || !can || !!on); }
document.addEventListener('fullscreenchange', fullBtn); document.addEventListener('webkitfullscreenchange', fullBtn);
function goFull() {   // полный экран по нажатию «Играть»: только внутри нажатия, иначе браузер откажет
  const d = document, el = d.documentElement;
  if (DESK || d.fullscreenElement || d.webkitFullscreenElement) return;   // ПК-версия: окно уже во весь экран (игрок мог сам выйти — не возвращаем)
  try {
    const r = (el.requestFullscreen || el.webkitRequestFullscreen || function () {}).call(el, { navigationUI: 'hide' });
    if (r && r.then) r.then(() => { try { screen.orientation.lock(LAND ? 'landscape' : 'portrait').catch(() => {}); } catch {} }).catch(() => {});
  } catch {}
}
const playing = new Set();
// сколько низа каждого звука отправить в «телефонный» слой обертонов
const SEND = { heart: 2.2, heart_held: 1, sub_presence: 3, drone_dread: 1.6, boom: 1.6, metal_groan: .7 };
function play(name, { when = 0, bus = 'sfx', loop = false, gain = 1, off = 0, rate = 1, to = null, cut = 0, pan = 0, lp = 0, rev = 0 } = {}) {
  if (name === 'jumpscare' && !to && directG) to = [[directG, 1]];
  if (bus === 'voice' && isVoice(name) && !BUF[name] && AC) voiceMiss++;
  if (!AC || !BUF[name]) return null;
  if (bus === 'voice' && TIM[name] && TIM[name].g) gain *= TIM[name].g;   // реплики выровнены по громкости (tools/story/voice.py loud)
  const s = AC.createBufferSource(), gn = AC.createGain(); s.buffer = BUF[name]; s.loop = loop; gn.gain.value = gain; s.playbackRate.value = rate;
  let head = s;
  if (lp) { const f = filt('lowpass', lp, .7); head.connect(f); head = f; }
  if (pan && AC.createStereoPanner) { const p = AC.createStereoPanner(); p.pan.value = Math.max(-1, Math.min(1, pan)); head.connect(p); head = p; }
  head.connect(gn);
  if (to) { for (const [nd, g2] of to) { if (g2 === 1) gn.connect(nd); else { const sg = AC.createGain(); sg.gain.value = g2; gn.connect(sg); sg.connect(nd); } } }
  else gn.connect(bus === 'voice' ? voiceG : bus === 'music' ? musicG : sfxG);
  if (rev && revIn) { const rg = AC.createGain(); rg.gain.value = rev; gn.connect(rg); rg.connect(bus === 'voice' ? voiceRev : sfxRev || revIn); }
  if (!to && bus === 'sfx' && SEND[name] && sfxLow) { const sg = AC.createGain(); sg.gain.value = SEND[name]; gn.connect(sg); sg.connect(sfxLow); }
  const at = AC.currentTime + Math.max(0, when);
  s.start(at, off);
  if (cut) { gn.gain.setValueAtTime(gain, at + Math.max(0, cut - .025)); gn.gain.linearRampToValueAtTime(0, at + cut); s.stop(at + cut + .01); }   // без щелчка на обрезе
  const o = { s, gn, name, at }; playing.add(o); s.onended = () => playing.delete(o); return o;
}
// звуки, запланированные на потом (стук через секунду и т. п.), не должны прозвучать на следующем кадре
function dropPending() { if (!AC) return; for (const o of [...playing]) if (!o.music && !o.voice && o.at > AC.currentTime + .03) { try { o.s.stop(); } catch {} playing.delete(o); } }
function stopAll(keepMusic = true) { for (const o of [...playing]) if (!(keepMusic && o.music)) { try { o.s.stop(); } catch {} playing.delete(o); } }
let curMusic = null, curMusicName = '';
function music(name) {
  if (!AC || name === curMusicName) return; curMusicName = name;
  const old = curMusic; if (old) { old.gn.gain.setTargetAtTime(0, AC.currentTime, .6); setTimeout(() => { try { old.s.stop(); } catch {} }, 2500); }
  if (name && !BUF[name] && rawSnd[name] && !decQ[name]) {   // музыка смены и финалов декодируется, когда понадобилась (иначе ~100 МБ лежали бы в памяти всю игру)
    decQ[name] = decodeOne(name, rawSnd[name]).then(() => { delete decQ[name]; if (curMusicName === name && !curMusic) { curMusicName = ''; music(name); } }); curMusic = null; return; }
  curMusic = name ? play(name, { bus: 'music', loop: true, gain: .0001 }) : null; if (name && !curMusic) curMusicName = null;   // ещё не скачана — попробуем позже
  if (curMusic) { curMusic.music = true; curMusic.gn.gain.setTargetAtTime(1, AC.currentTime, .8); }
  // в «напряжении» середина и так есть — обертонов ему почти не надо; гулу — полностью
  if (musicSend) musicSend.gain.setTargetAtTime(name === 'tension' ? .35 : 1, AC.currentTime, .5);
}
let ambient = {};
function amb(name, on, gain = .5) {
  if (!AC) return;
  if (on && !ambient[name]) { const o = play(name, { loop: true, gain: .0001 }); if (!o) return; o.music = true; o.gn.gain.setTargetAtTime(gain, AC.currentTime, .4); ambient[name] = o; }
  if (!on && ambient[name]) { const o = ambient[name]; o.gn.gain.setTargetAtTime(0, AC.currentTime, .3); setTimeout(() => { try { o.s.stop(); } catch {} }, 1500); delete ambient[name]; }
}
// перед скримером звук уходит в тишину (фон почти в ноль за четверть секунды, музыка — тоже), потом удар
let hushOn = false;
function hush(on, soft) {
  if (on === hushOn) return; hushOn = on; if (!AC) return;
  if (musicG) musicG.gain.setTargetAtTime(on ? .012 : .55, AC.currentTime, on ? .08 : .05);
  if (on && !soft && bed && bed.src.room_tone) play('hum_stop', { gain: .5 });   // явная тишина кадра: где-то выключился гудящий холодильник
  amb('tinnitus', on, soft ? .3 : .9);
  bedApply();
}
function duck(on) { if (musicG && !hushOn) musicG.gain.setTargetAtTime(on ? .22 : .55, AC.currentTime, .15); }

/* ---------- живой фон места: гул помещения, дождь, лампы — никогда не цифровая тишина ---------- */
let bedG = null, bed = null, bedLow = null;
const BEDS = { lobby: { tone: .24, f: 9000, hum: .0025 }, corridor: { tone: .2, f: 6500, hum: .009 }, server: { tone: .32, f: 650, hum: .0015 } };
// дождь: снаружи — открыто; у стекла — глуше; в холле — далеко; на этажах и в подвале почти не слышно
const RAINS = { out: [.2, 9000], glass: [.11, 2400], in: [.045, 750], far: [.01, 420], none: [0, 420] };
function bedInit() {
  bedG = AC.createGain(); bedG.gain.value = 0; bedG.connect(uAmb);   // фон места (дождь, гул) — ползунок «Дождь и фон»
  bedLow = AC.createGain(); bedLow.gain.value = VOL.amb; bedLow.connect(lowSend);   // и его низ для динамика телефона — через тот же ползунок
  const bs = AC.createGain(); bs.gain.value = .6; bedG.connect(bs); bs.connect(bedLow);
  bed = { toneF: filt('lowpass', 9000, .7), toneG: AC.createGain(), rainF: filt('lowpass', 750, .7), rainG: AC.createGain(), humG: AC.createGain(), src: {}, key: '', lv: -1 };
  bed.toneF.connect(bed.toneG); bed.toneG.connect(bedG); bed.rainF.connect(bed.rainG); bed.rainG.connect(bedG); bed.humG.connect(bedG);
  bed.toneG.gain.value = bed.rainG.gain.value = bed.humG.gain.value = 0;
  // гул ламп дневного света: 100 Гц «пилой» через полосовой фильтр — синтез, без файла
  try { const o = AC.createOscillator(); o.type = 'sawtooth'; o.frequency.value = 100; const bp = filt('bandpass', 850, .9); o.connect(bp); bp.connect(bed.humG); o.start(); } catch {}
}
let bedWant = { loc: 'lobby', rain: 'in', lv: 0 };
function bedApply() {
  if (!bed || !AC) return;
  for (const [k, dst] of [['room_tone', bed.toneF], ['rain', bed.rainF]]) if (!bed.src[k] && BUF[k]) {   // петли запускаются, как только раскодированы
    const s = AC.createBufferSource(); s.buffer = BUF[k]; s.loop = true; s.connect(dst); s.start(0, Math.random() * BUF[k].duration); bed.src[k] = s;
  }
  const t = AC.currentTime, b = BEDS[bedWant.loc] || BEDS.lobby, r = RAINS[bedWant.rain] || RAINS.in, key = bedWant.loc + bedWant.rain;
  if (key !== bed.key) { bed.key = key;
    bed.toneG.gain.setTargetAtTime(b.tone, t, .6); bed.toneF.frequency.setTargetAtTime(b.f, t, .3); bed.humG.gain.setTargetAtTime(b.hum, t, .6);
    bed.rainG.gain.setTargetAtTime(r[0], t, .5); bed.rainF.frequency.setTargetAtTime(r[1], t, .3); }
  const lv = hushOn ? .045 : bedWant.lv;
  if (Math.abs(lv - bed.lv) > .01) { bed.lv = lv; bedG.gain.setTargetAtTime(lv, t, hushOn ? .08 : .35); }
}
function bedSet(loc, rain, lv) { if (loc) bedWant.loc = loc; if (rain) bedWant.rain = rain; if (lv != null) bedWant.lv = lv; bedApply(); }

/* ---------- синтез мелочей: капля, звон стекла, треск лампы ---------- */
function env(gn, t, a, peak, dec) { gn.gain.setValueAtTime(0, t); gn.gain.linearRampToValueAtTime(peak, t + a); gn.gain.exponentialRampToValueAtTime(.0001, t + a + dec); }
function panTo(node, pan) { if (pan && AC.createStereoPanner) { const p = AC.createStereoPanner(); p.pan.value = pan; node.connect(p); return p; } return node; }
function drip(gain, pan) {
  if (!AC) return; const t = AC.currentTime + .01, o = AC.createOscillator(), gn = AC.createGain(), f0 = 900 + Math.random() * 800;
  o.type = 'sine'; o.frequency.setValueAtTime(f0, t); o.frequency.exponentialRampToValueAtTime(f0 * .42, t + .06); env(gn, t, .003, gain, .1);
  o.connect(gn); const out = panTo(gn, pan); out.connect(sfxG); const rg = AC.createGain(); rg.gain.value = .6; out.connect(rg); rg.connect(sfxRev); o.start(t); o.stop(t + .15);
}
function tink(gain, f) { if (!AC) return; const t = AC.currentTime, o = AC.createOscillator(), gn = AC.createGain(); o.type = 'sine'; o.frequency.value = f; env(gn, t, .002, gain, .08); o.connect(gn); gn.connect(sfxG); o.start(t); o.stop(t + .12); }
function tubeBuzz(gain) {
  if (!AC) return; const t = AC.currentTime, o = AC.createOscillator(), gn = AC.createGain(), bp = filt('bandpass', 1100, 1.2);
  o.type = 'sawtooth'; o.frequency.value = 100; env(gn, t, .004, gain, .07); o.connect(bp); bp.connect(gn); gn.connect(sfxG); o.start(t); o.stop(t + .1);
}
// менюшный «тук»: щелчок и низкий удар — весомо, но тихо
function uiSnd(kind) {
  if (!AC) return; const t = AC.currentTime;
  if (kind === 'open') { play('static', { cut: .14, gain: .35 }); play('boom', { gain: .35, lp: 500 }); return; }
  play('click', { gain: .55, rate: kind === 'back' ? .8 : 1 }); const o = AC.createOscillator(), gn = AC.createGain(); o.type = 'sine'; o.frequency.setValueAtTime(kind === 'back' ? 70 : 95, t); o.frequency.exponentialRampToValueAtTime(45, t + .12);
  env(gn, t, .004, .35, .14); o.connect(gn); gn.connect(sfxG); o.start(t); o.stop(t + .2);
}

/* ---------- случайные далёкие звуки места: каждые 8–20 с, иногда — ничего ---------- */
const AMBP = {
  lobby: [{ n: 'lift_ding_far', g: .16 }, { n: 'metal_groan', g: .09, lp: 900, cut: 2.6 }, { n: 'door_slams', off: .5, cut: .35, g: .11, lp: 450 },
          { n: 'steps', cut: .75, g: .07, lp: 700 }, { n: 'knock', cut: .13, rate: .55, g: .1, lp: 500 }, { n: 'boom', g: .12, lp: 260, rate: .8 }],
  corridor: [{ n: 'steps', cut: .75, g: .08, lp: 900 }, { n: 'metal_groan', off: .2, cut: 1.6, g: .07, lp: 1600, rate: 1.3 }, { n: 'door', cut: .55, g: .07, lp: 800 },
             { n: 'fluor_flicker', off: 1.4, cut: .5, g: .12 }, { n: 'whispers', off: 1.6, cut: 1.1, g: .035, lp: 2200, rare: .3 }],
  server: [{ n: '#drip', g: .1 }, { n: '#drip', g: .07 }, { n: 'metal_groan', g: .08, lp: 500, rate: .8 }, { n: 'knock', cut: .13, rate: .45, g: .12, lp: 380 },
           { n: 'steps_barefoot', off: 1.85, cut: .6, g: .05, lp: 700 }],
  title: [{ n: 'boom', g: .16, lp: 260, rate: .8 }, { n: 'lift_ding_far', g: .1 }, { n: 'metal_groan', g: .06, lp: 700, cut: 2.4 }]
};
let ambNext = 5, ambLast = '';
function oneShot(it, when = 0) {
  const pan = it.pan ?? (Math.random() * 1.6 - .8), gk = .8 + Math.random() * .45, rate = (it.rate || 1) * (.9 + Math.random() * .2);
  if (it.n === '#drip') { drip(it.g * gk, pan); if (Math.random() < .5) setTimeout(() => drip(it.g * gk * .7, pan), 260 + Math.random() * 500); return; }
  play(it.n, { off: it.off || 0, cut: it.cut || 0, gain: it.g * gk, lp: it.lp || 0, pan, rate, rev: it.dry ? 0 : .35, when });
}
function ambTick(T, pool, quiet) {
  if (!AC || T < ambNext) return;
  if (quiet) { ambNext = T + 2 + Math.random() * 3; return; }   // кто-то говорит или скоро скример — подождём
  ambNext = T + 8 + Math.random() * 12;
  const P = AMBP[pool]; if (!P || Math.random() < .25) return;
  let it, n = 0; do it = P[Math.random() * P.length | 0]; while ((it.n === ambLast || (it.rare && Math.random() > it.rare)) && ++n < 10);
  ambLast = it.n; oneShot(it);
}
// ненадёжная лампа места (World.tube): гаснет — щелчок и треск
let tubePrev = 1, tubeLastT = -9;
function tubeTick(T) {
  if (!window.World || !World.ok) return; const v = World.tube;
  if (tubePrev >= .5 && v < .5) { tubeBuzz(.05); if (T - tubeLastT > 1.2) play('fluor_flicker', { off: 1.45, cut: .3, gain: .12 }); tubeLastT = T; }
  tubePrev = v;
}

/* ---------- стук Кати: звук в момент касания кулака (A.katya.knockHit), высота ±8 % ---------- */
const kArm = {}; let kLast = -9;
function actors() { return window.__dbgA || null; }
function onScreen(C) { return C && C.root && C.root.parent && lastDrawn === (beat && beat.img || lastDrawn); }
function katyaKnocking() { const A = actors(); if (!A) return false; for (const k of ['katya', 'katyaSmile']) { const C = A[k]; if (C && C.root.parent && (C.pose === 'knock' || C.pose === 'slam')) return true; } return false; }
function knockSnd(gain) {
  if (!AC || !BUF.knock) return; const t = AC.currentTime, rate = 1 + (Math.random() * 2 - 1) * .08;
  const s = AC.createBufferSource(), hp = filt('highpass', 240, .7), pk = filt('peaking', 2300, 1.4), gn = AC.createGain();
  pk.gain.value = 7; s.buffer = BUF.knock; s.playbackRate.value = rate; gn.gain.setValueAtTime(gain, t); gn.gain.setTargetAtTime(0, t + .1, .02);
  s.connect(hp); hp.connect(pk); pk.connect(gn); gn.connect(sfxG); const rg = AC.createGain(); rg.gain.value = .25; gn.connect(rg); rg.connect(sfxRev);
  s.start(t, 0); s.stop(t + .2);
  tink(.035 * gain, 2900 * rate);   // стекло чуть звенит
}
function knockSync(T) {
  const A = actors(); if (!A || !AC) return;
  for (const k of ['katya', 'katyaSmile']) {
    const C = A[k]; if (!C || !C.root || !C.root.parent || (C.pose !== 'knock' && C.pose !== 'slam')) { kArm[k] = true; continue; }
    const h = C.knockHit || 0, close = /face|smile|slam/.test(lastDrawn) ? 1 : /cam|entrance/.test(lastDrawn) ? .45 : .8;
    if (C.pose === 'knock') {
      if (kArm[k] !== false && h > .3) { kArm[k] = false; knockSnd(.62 * close * (voiceNow ? .7 : 1)); if (T - kLast > .6) buzz(16); kLast = T; }
      else if (h < .12) kArm[k] = true;
    } else {   // ладони бьют в стекло
      if (kArm[k] !== false && h > .96) { kArm[k] = false; if (T - slamHitT > .8) { play('glass_slam', { off: .45, cut: .45, gain: .32 * close, rate: .92 + Math.random() * .12 }); play('knock', { cut: .13, rate: .6, gain: .5 * close, lp: 900 }); buzz(30); } }
      else if (h < .6) kArm[k] = true;
    }
  }
}
let slamHitT = -9;

/* ---------- «подпись» Жильца: сухой хруст шеи и вдох у самого уха — за 2–4 с до того, как он появится ---------- */
const SIG_AT = { cam13_turn: 2.6, cam13_mih_monster: 3.35, corridor_off: 1.7, cleaner_grab: 1.0 };   // когда он появляется в кадре, если не сразу
const SIG_SKIP = /^(hall_cam|door_smile|lunge_|cam7_jump|cover_door)$|^lunge_/;   // далеко у лифта / только отражение (молча, так задумано) / бросок со своим вдохом
const MON = {};
function monsterIn(img) {
  if (!img || !window.World || !World.shots) return false; const id = (World.alias && World.alias[img]) || img;
  if (!(id in MON)) { const sh = World.shots[id]; MON[id] = !!sh && /['"]monster['"]/.test(String(sh.set)); }
  return MON[id];
}
function sigWanted(b, prev) {
  if (!b || !b.img || b.fx === 'lunge' || SIG_SKIP.test(b.img) || !monsterIn(b.img)) return false;
  if (prev && prev.img && monsterIn(prev.img) && (SIG_AT[b.img] == null)) return false;   // уже был в кадре — это не появление
  if ((b.sfx || []).some(s => s[0] === 'neck_crack') || (b.hit || []).includes('neck_crack')) return false;
  return true;
}
function signature(k = 1) {
  if (!AC) return; const pan = (Math.random() < .5 ? -1 : 1) * (.25 + Math.random() * .35);
  play('neck_crack', { off: .55, cut: .45, gain: .5 * k, pan });
  play('breath_close', { off: .05, cut: 1.2, gain: .4 * k, pan: pan * .5, when: .4 });
}

/* ---------- время (останавливается, когда вкладка скрыта) ---------- */
let clockT = 0, lastP = performance.now(), paused = false;
function now() { return clockT; }
let userPause = false;   // ☰ в игре: пауза, пока открыто меню паузы / настройки
document.addEventListener('visibilitychange', () => { paused = document.hidden || userPause; if (AC) paused ? AC.suspend() : AC.resume(); if (!document.hidden) lastP = performance.now(); });   // вернулись во вкладку: пока её не было, звук стоял — часы не прыгают вперёд

/* ---------- состояние ---------- */
let mode = 'load', scene = null, sceneId = '', bi = 0, beat = null, bT0 = 0, bDur = 0, bCuts = null, sched = [], fired = new Set(), onBeatEnd = null;
let gen = 0;   // номер текущей сцены: старые таймеры и мини-игры по нему понимают, что устарели
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
let perf2S = 0, perf2N = 0;
const JUMP_D = .75;   // сколько длится скример смерти (с)
const GORE_D = 3.4;   // после скримера: как оно убивает (кадр gore@… в scenes.js, этапы: захват → когти → рывок → ест), потом чернота
// у каждой смерти своё (владелец: «больше жести, во всех 14 уникальное»): L — «Лили», D — двойник, F — жертва Фрэнк
const GORE_KIND = { over_k1: 'face', over_k2: 'hang', over_k3: 'eat', over_k4: 'neck', over_k5: 'drag', over_k5b: 'drag', over_k6: 'limb', over_k7: 'chest', over_k8: 'neckF',
  over_k9: 'throatL', over_fmom: 'throat', over_door: 'chestL', over_come: 'heart', over_stare: 'eyes', over_stare_b: 'eyes', over_phone: 'faceD' };
const goreKind = id => GORE_KIND[id] || GORE_KIND[id.replace(/_(srv|hide)$/, '')] || null;
// откуда снимать финал: последний кадр смерти, а где он тесный (будка, стол) — соседний кадр той же локации с открытым местом
const GORE_BASE = { over_k3: 'dbl_cam', over_k5: 'dbl_cam', over_k5b: 'dbl_cam', over_k7: 'bm_view', over_stare: 'door_tilt', over_stare_b: 'door_tilt', over_stare_hide: 'door_tilt' };
let jumpBuzz = 0;
let flashT = -9, blackT = -9, scareAt = null, shakeAmp = 0, typed = null, hb = 0, pre = 0, trig = [], subl = null, sublScene = '', voiceNow = false;
let creep = null, creepCool = 25, mgView = null;   // случайное появление Жильца в кадре; кадр под мини-игрой
let sceneT0 = 0, secBase = 7, lastTime = '02:55', lastDrawn = 'door_katya', falseAlarm = false;
const flags = Object.assign({}, LS.get('flags', {}));   // что уже случилось за ночь: дверь открыта, найдена соль, осмотрено место (v:сцена)…
// условие сценария: 'salt' — флаг есть, '!salt' — нет; массив — все сразу
const flagOk = c => !c || [].concat(c).every(k => k[0] === '!' ? !flags[k.slice(1)] : !!flags[k]);
function setFlags(f) { if (!f) return; for (const k of [].concat(f)) flags[k] = 1; LS.set('flags', flags); }
const buzz = ms => { try { if (navigator.vibrate && !LS.get('mute', false)) navigator.vibrate(ms); } catch {} };
const seen = new Set(LS.get('seen', []));
const frozenC = document.createElement('canvas'); frozenC.width = 1080; frozenC.height = 1920; const frozenG = frozenC.getContext('2d');
const stillC = document.createElement('canvas'); let stillCache = null;   // готовый кадр старого фото (fx 'photo'): считается один раз на план
let audioQ = 0, voiceLate = 0, beatRun = 0, talkWho = null, talkFx = '', holdT = 0, reactW = null;   // audioQ — звук кадра ждёт первого нарисованного кадра (иначе голос уходил вперёд чёрной склейки); voiceLate — реплик, догнанных после старта кадра (медленная сеть)
const cur0 = () => { let c = null; for (const it of sched) if (now() - bT0 >= it.t) c = it; return c; };   // текущая реплика кадра (для talkFx)

function durOf(k) { return (TIM[k] && TIM[k].dur) || (BUF[k] && BUF[k].duration) || 2; }
// кто говорит: имя на табличке и цвет
const SPK = { 'ИТАН': { c: '#cfd3da', cls: 'narr', sub: 'ты' }, 'СМЕНЩИК': { c: '#ffd27a' }, 'ЛИЛИ': { c: '#ffb36b' }, 'ФРЭНК': { c: '#8fd0ff' },
  'ГАС': { c: '#9fd8a8' }, '???': { c: '#ff3030', cls: 'monster' }, 'КАМЕРА': { c: '#7fe0a0' }, 'ТЕЛЕФОН': { c: '#9aa3b5' },
  'ВЕРА': { c: '#7fd4ff', sub: 'пульт «Щит»' }, 'ГОЛОС': { c: '#c8b4ff' } };
const VOICE_WHO = { ch_katya: 'ЛИЛИ', ch_whisper: 'ЛИЛИ', ch_monster: '???' };
const whoOf = k => VOICE_WHO[k] || (TIM[k] && TIM[k].who) || 'ИТАН';
// вслух или про себя: рассказ Итана (без тире) — мысли, губы не двигаются; реплики с тире и все остальные — вслух
const aloudOf = k => whoOf(k) !== 'ИТАН' || ((TIM[k] || {}).t || '').trim().startsWith('—');
// губы по тексту: в слове, которое звучит сейчас (время слов — в timing.json), берём букву под этот момент → форма рта (визема)
const VIS_CH = (() => { const m = {}, set = (s, v) => { for (const c of s) m[c] = v; };
  set('аяa', 'viseme_aa'); set('оёo', 'viseme_O'); set('уюuw', 'viseme_U'); set('ыийiy', 'viseme_I'); set('еэe', 'viseme_E');
  set('пбмpbm', 'viseme_PP'); set('фвfv', 'viseme_FF'); set('сзцsz', 'viseme_SS'); set('шжщчjc', 'viseme_CH'); set('тдtd', 'viseme_DD');
  set('нлnl', 'viseme_nn'); set('кгхkgqxh', 'viseme_kk'); set('рr', 'viseme_RR');
  set('áàâãäå', 'viseme_aa'); set('óòôõö', 'viseme_O'); set('úùûü', 'viseme_U'); set('íìîïіїý', 'viseme_I'); set('éèêëє', 'viseme_E'); set('ñ', 'viseme_nn'); set('çß', 'viseme_SS'); set('ґ', 'viseme_kk');   // испанский, португальский, немецкий, украинский
  return m; })();
function visemeAt(k, tl) {
  const tm = TIM[k] || {}, w = tm.ew || tm.w; if (!w || !w.length) return null;   // ew — слова английской записи: язык с субтитрами (японский, французский…) звучит английскими голосами — губы под то, что слышно
  let i = -1; for (let j = 0; j < w.length && w[j][0] <= tl; j++) i = j;
  if (i < 0) return 'sil';
  const L = [...String(w[i][1]).toLowerCase()].filter(c => VIS_CH[c]); if (!L.length) return /\p{L}/u.test(w[i][1]) ? null : 'sil';   // буквы без карты (иероглифы, хангыль) — рот по громкости голоса, а не закрыт
  const st = w[i][0], nx = i + 1 < w.length ? w[i + 1][0] : st + 9, en = Math.min(nx - .02, st + .075 * L.length + .1);   // слово кончилось — пауза, рот закрыт
  if (tl > en) return 'sil';
  return VIS_CH[L[Math.min(L.length - 1, Math.floor((tl - st) / Math.max(.05, en - st) * L.length))]];
}
let waiting = false, holding = false, auto = LS.get('auto', false);

// где мы: место кадра (холл / этажи / подвал) и слышен ли дождь
function shotInfo(img) { const W3 = window.World; if (!img || !W3 || !W3.shots) return null; return W3.shots[(W3.alias && W3.alias[img]) || img] || null; }
function placeOf(img, fx) {
  const sh = shotInfo(img); if (!sh) return null;
  const loc = sh.loc || 'lobby';
  let rain = loc === 'lobby' ? 'in' : loc === 'corridor' ? 'far' : 'none';
  if (loc === 'lobby') { if (sh.pos && sh.pos[2] > 6.2) rain = 'out'; else if ((sh.look && sh.look[2] > 6) || fx === 'rain') rain = 'glass'; }
  return { loc, rain, out: rain === 'out' };
}
// тихие перемены в холле: часы встали на 03:00, куртка Михалыча пропала после открытой двери, фото на пропуске исцарапано
function propsNow() {
  const book = flags.jread ? 0 : 1;   // журнал Фрэнка на столе перевязан, пока Итан его не прочитал (02:50)
  if (mode === 'shift' && shf) return { sh: shiftProps(), book, jacket: flags.opened ? 0 : 1, badge: flags.mih ? 'scratched' : 1, fphone: flags.fphone ? 1 : 0 };   // куртка и пропуск на камерах смены — как в сценах (в A1 куртка висела снова, а под стойкой её не было)
  const tm = toMin(lastTime) < toMin('03:00') ? lastTime : '03:00';   // до трёх часы идут (ночь с 21:00 — строки «21:00» < «03:00» не сравнить: в прологе стояли на трёх), в три встают
  return Object.assign({ clock: tm, jacket: flags.opened ? 0 : 1, badge: flags.mih ? 'scratched' : 1, book, fphone: flags.fphone ? 1 : 0 }, scene && scene.props, beat && beat.props);   // fphone — телефон Фрэнка на зарядке на столе
}
function gameClock(withSec = true) {
  const [h, m] = lastTime.split(':').map(Number), s = h * 3600 + m * 60 + secBase + Math.floor(Math.max(0, now() - sceneT0)), p2 = v => String(v).padStart(2, '0');
  return p2(Math.floor(s / 3600) % 24) + ':' + p2(Math.floor(s / 60) % 60) + (withSec ? ':' + p2(s % 60) : '');
}

/* ---------- журнал прохождения (для экрана концовки): выборы, записи смены, время в игре ---------- */
const runNew = () => ({ picks: [], sec: 0, rec: 0, ok: 0, miss: 0, hrs: [] }), PLAYING = new Set(['play', 'choice', 'mg', 'cams', 'shift']);
let run = Object.assign(runNew(), LS.get('run', {})), runSaveT = 0;
const runSave = () => LS.set('run', run);
function runPick(sc, q, a, i) {   // выбор в сцене sc; вернулся к этому выбору — всё, что было после него, переписывается. i — номер варианта: в журнале ночи текст берётся на текущем языке
  const j = run.picks.findIndex(p => p.sc === sc); if (j >= 0) run.picks.length = j;
  run.picks.push({ sc, q, a, i }); runSave();
}
const pickTh = p => p.i != null && ((S.scenes[p.sc] || {}).then || {}), pickA = p => { const th = pickTh(p); return th && th.opts && th.opts[p.i] ? th.opts[p.i].t : tr(p.a); }, pickQ = p => { const th = pickTh(p); return th && th.choice ? th.choice : tr(p.q); };   // сменил язык посреди ночи — решения в журнале не остаются на старом
// выбор, от которого зависит концовка (а не «смерть или дальше»): к нему можно вернуться с экрана концовки
const forkAt = sc => { const t = (S.scenes[sc] || {}).then; return !!(t && t.opts && t.opts.filter(o => !/^over_/.test(o.go)).length > 1); };

function startScene(id, at) {   // at: true — сразу к концу сцены (последний кадр, потом выбор/мини-игра); число — с этого кадра (продолжение после перезагрузки)
  if (!S.scenes[id]) { console.warn('нет сцены', id); return menu(); }   // сохранение из старой версии сценария не ломает игру
  gen++; stopAll(); choiceEl.classList.add('hide'); mgEl.classList.add('hide'); mgEl.innerHTML = ''; mgView = null; act = null; actEl.innerHTML = ''; actEl.classList.add('hide');
  uiEl.innerHTML = ''; $('hud').classList.remove('hide'); overBg = null;
  sceneId = id; scene = S.scenes[id]; mode = 'play'; sceneT0 = now(); document.body.classList.remove('shifting'); secBase = scene.sec ?? 7; sublScene = '';
  if (/^(branch_a|hide|run)/.test(id)) setFlags('opened');   // через setFlags — запоминается и после перезагрузки
  if (/^(server|down|basement|knock3|dawn|stay|call540|morning)/.test(id)) setFlags('mih');
  if (scene.music) music(scene.music);
  if (scene.time) { lastTime = scene.time; clockEl.textContent = scene.time; clockEl.classList.remove('hide'); }
  if (!id.startsWith('over_')) { LS.set('save', id); LS.set('saveT', lastTime); }   // и время ночи: у сцен осмотра и разговоров своего нет — после «Продолжить» часы шли бы с 02:55
  bi = at === true ? scene.beats.length - 2 : typeof at === 'number' ? Math.min(at, scene.beats.length) - 1 : -1;
  for (let i = 0; i <= bi; i++) if (scene.beats[i] && flagOk(scene.beats[i].if)) setFlags(scene.beats[i].set);   // начали с середины (продолжение, «⏮», повтор с решения) — отметки пропущенных кадров тоже ставим (журнал «прочитан» и т. п.)
  const my = gen, ready = voicePrep(id); holdN = -1;
  if (sceneKeys(scene).every(k => !isVoice(k) || BUF[k])) return nextBeat();
  beat = null; audioQ = 0; Promise.race([ready, new Promise(r => setTimeout(r, 700))]).then(() => { if (gen === my && mode === 'play' && !beat) nextBeat(); });   // реплики ещё декодируются — короткая чёрная склейка
}
function nextBeat() {
  bi++; while (scene.beats[bi] && !flagOk(scene.beats[bi].if)) bi++;   // кадр только при условии (что нашёл, что спросил)
  if (scene.beats[bi]) setFlags(scene.beats[bi].set);
  amb('heart', false); holding = false; waiting = false; tapEl.classList.add('hide'); dlgEl.classList.remove('wait', 'wait2');
  const carry = trig.filter(e => !e.done && e.carry); trig = [];
  if (!scene.beats[bi]) { seen.add(sceneId); LS.set('seen', [...seen]); carry.forEach(e => e.fn()); return runThen(scene.then); }
  if (hushOn) hush(false);
  dropPending();
  // тот же кадр подряд — действие продолжается, а не начинается заново (никто не телепортируется назад)
  { const k = scene.beats.indexOf(beat), pv = k >= 0 && k < bi ? beat : null, nb = scene.beats[bi], same = pv && pv.img && pv.img === nb.img;   // прошлый показанный кадр этой сцены (кадр по условию мог быть пропущен)
    shotOff = same ? shotOff + (now() - bT0) : 0; darkHold = !!(same && pv.fx === 'dark'); }
  const prevBeat = beat;
  beat = scene.beats[bi]; bT0 = now(); fired.clear(); sched = []; typed = null; scareAt = null; hb = 0; audioQ = 0; beatRun++;
  if (beat.hush && AC) hush(true, beat.hush === 'soft');   // 'soft' — тишина перед ударом без щелчка холодильника и с тихим звоном
  if (beat.time) { lastTime = beat.time; secBase = 0; sceneT0 = now(); clockEl.textContent = beat.time; }   // часы перещёлкнулись посреди сцены
  chapEl.classList.toggle('hide', !beat.chapter); if (beat.chapter) chapEl.innerHTML = `<b>${beat.chapter[0]}</b><span>${beat.chapter[1]}</span>`;
  let t = beat.sayAt ?? .15;   // sayAt — реплика не сразу, а с этой секунды кадра (голос Фрэнка из темноты после паузы)
  for (const k of beat.say || []) {
    const ln = !TIM[k] && (S.shift.lines || {})[k];   // реплика ещё без озвучки — печатается текстом
    if (ln) { const tx = tr(ln), d = Math.max(2.2, tx.length / (CPS[LNG] || 14) + 1.3); typed = { t0: t, d, s: tx, who: (S.shift.who || {})[k] }; t += d + .3; continue; }
    sched.push({ t, k, d: durOf(k) });
    if (beat.scareGap && k === beat.say[0]) scareAt = t + gapTime(k);
    t += (SUB_ONLY ? Math.max(durOf(k), readT(k)) : durOf(k)) + .3;   // субтитры под английскую запись: следующая реплика ждёт, пока эту дочитают (японский читают вдвое дольше, чем она звучит)
  }
  if (beat.text) { const d = Math.max(2.2, beat.text.length / (CPS[LNG] || 14) + 1.3); typed = { t0: beat.say ? t : .2, d, s: beat.text }; t = Math.max(t, typed.t0 + d); }
  if (beat.scare != null) scareAt = beat.scare;
  creep = null;
  bDur = Math.max(t + (beat.pad || .3), 1.2);
  if (sceneId.startsWith('over_') && beat.black != null) bDur = Math.max(bDur, beat.black + JUMP_D + (goreKind(sceneId) ? GORE_D : 0) + .3);   // скример и финал смерти успевают до экрана смерти
  // склейки по словам: cutAt — слова первой реплики (regexp, русское|английское), на которых меняется план cams; без них планы делят кадр поровну
  bCuts = null;
  if (beat.cutAt && sched.length) { const it = sched[0], w = (TIM[it.k] || {}).ew || (TIM[it.k] || {}).w || [];   // ew — слова английской записи (языки без своей озвучки): склейка ровно на звучащем слове
    bCuts = [0, ...beat.cutAt.map((re, i) => { const r = new RegExp(re, 'i'), hit = w.find(x => r.test(x[1])); return hit ? it.t + hit[0] : bDur * (i + 1) / (beat.cutAt.length + 1); })];
    for (let i = 1; i < bCuts.length; i++) bCuts[i] = Math.max(bCuts[i], bCuts[i - 1] + .5); }
  // реакции тела (кивнул / покачал головой): react = { КТО: [что, с секунды, по секунду] } или [что, [реплика, слово-regexp, доля реплики], сколько секунд] — к слову,
  // а не к секунде кадра: в английской и украинской версии слова звучат в другое время («это не она» — качает головой на «это не она», а не за секунду до)
  reactW = null;
  if (beat.react) { reactW = {}; for (const who in beat.react) { const r = beat.react[who]; let a = r[1] ?? 0, b = r[2] ?? 1e9;
    if (Array.isArray(a)) { const it = sched.find(x => x.k === a[0]), re = new RegExp(a[1], 'i'), w = it && ((TIM[it.k] || {}).ew || (TIM[it.k] || {}).w || []).find(x => re.test(x[1])); a = w ? it.t + w[0] : it ? it.t + (a[2] || 0) * it.d : 0; b = a + (r[2] ?? 2); }   // слова нет (новый язык) — доля реплики a[2]
    reactW[who] = [r[0], a, b]; } }
  // смс на телефон: баннер сверху; кадр не кончится, пока её не дочитать
  for (const m of [].concat(beat.sms || [])) { const at = m.at ?? .5; trig.push({ at, fn: () => showSms(m) }); bDur = Math.max(bDur, at + 3.2); }
  // перед скримером — не меньше 1.2 с тишины: если удар в самом начале кадра, прошлый кадр замирает и темнеет в тишине;
  // fx 'glitch' — прошлый кадр замирает на 0.8 с с надписью «соединение потеряно», потом жёсткая склейка
  pre = beat.fx === 'glitch' ? .8 : scareAt != null && scareAt < 1.2 && beat.fx !== 'lunge' ? 1.3 - scareAt : 0;
  if (pre) { frozenG.drawImage(cv, 0, 0); bT0 = now() + pre; if (beat.fx === 'glitch') glitchStart(); }
  ruleEl.classList.toggle('hide', !beat.rule); if (beat.rule) { ruleEl.textContent = beat.rule; learnRule(); }
  // Жилец иногда мелькает в кадре: только на отмеченных кадрах, не чаще раза в ~40 секунд и не каждый раз
  if (beat.creep && beat.img && scareAt == null && clockT > creepCool && Math.random() < .6) {
    const at = 1 + Math.random() * Math.max(.5, bDur - 2);
    creep = { at, len: .35 + Math.random() * .4, seed: 1 + Math.floor(Math.random() * 1000) }; creepCool = clockT + 40 + Math.random() * 30;
  }
  // «подпись» Жильца: если он появится в этом кадре позже — за 2.5 с до этого; если сразу в следующем — в конце этого кадра
  if (sigWanted(beat, prevBeat)) { const ap = SIG_AT[beat.img] ?? 0; if (ap >= 2) trig.push({ at: ap - 2.5, fn: () => signature() }); else if (!carry.some(e => e.sig)) trig.push({ at: .05, fn: () => signature() }); }
  { const nb = scene.beats[bi + 1]; if (sigWanted(nb, beat) && (SIG_AT[nb.img] ?? 0) < 2) trig.push({ at: Math.max(.3, bDur - 2.5 + (SIG_AT[nb.img] || 0)), fn: () => signature(), carry: true, sig: true }); }
  // и один раз за ночь — ложная тревога: хруст и вдох, а в кадре никого
  if (!falseAlarm && beat.creep && !creep && scareAt == null && !beat.hush && Math.random() < .35) { falseAlarm = true; trig.push({ at: 1 + Math.random() * Math.max(.5, bDur - 2), fn: () => signature(.8) }); }
  // кадр-вспышка лица (1–2 кадра), не больше одной за сцену; выключается настройкой «меньше вспышек»
  subl = null;
  if (beat.subl != null && !calm() && sublScene !== sceneId) { const o = typeof beat.subl === 'object' ? beat.subl : { at: +beat.subl || 1 }; subl = { at: o.at ?? 1, img: o.img || 'monster_close', n: 0 }; }
  carry.forEach(e => e.fn());
  if (beat.wait) { waiting = true; tapEl.innerHTML = ICON.play + escH(beat.wait); tapEl.classList.remove('hide'); dlgEl.classList.add('hide'); ruleEl.classList.add('hide'); return; }
  audioQ = 1;   // звук — после первого нарисованного кадра (frame)
}
function beginBeat() { waiting = false; tapEl.classList.add('hide'); chapEl.classList.add('hide'); if (beat.rule) ruleEl.classList.remove('hide'); bT0 = now() + pre; play('click'); audioQ = 1; }
// реплика ещё не докачалась/не декодировалась (медленная сеть): когда готова — если её время ещё не пришло, ставим на своё время;
// если чуть опоздали — отсчёт кадра сдвигается на столько же (субтитры и голос идут вместе); сильно — голос с середины, чтобы догнать слова
const lateQ = [];   // докачанные реплики включаются в начале следующего кадра (frame → lateFlush): там часы свежие, а не между кадрами
function lateVoice(it, P) { const my = gen, b = beat; voiceReady([it.k]).then(() => { if (gen === my && beat === b && BUF[it.k]) lateQ.push({ it, P, my, b }); }); }
function lateFlush(T) {
  while (lateQ.length) { const { it, P, my, b } = lateQ.shift(); if (gen !== my || beat !== b || mode !== 'play' || !BUF[it.k] || waiting) continue;
    const late = T - (bT0 + it.t + P); let off = 0;
    if (late > 0) { voiceLate++; if (late <= 2.5) bT0 += late; else off = late; }
    if (off >= it.d - .4) { voiceMiss++; continue; }
    for (const vo of voiceFx(it.k)) { const o = play(it.k, Object.assign({ bus: 'voice' }, vo, { when: Math.max(0, -late) + (vo.dt || 0), off })); if (o) o.voice = true; } }
}
const burstOf = op => !!(op && (op.once || op.cut));   // ['static', t, { cut }] — короткий удар помех в секунду t, а не шипение на весь кадр
function beatAudio() {
  const P = pre;   // все звуки кадра сдвигаются на длину пред-кадра
  for (const [n, at, op] of beat.sfx || []) {   // [звук, секунда, {gain, cut, off, lp, pan, rate}]
    if (n === 'static' && !burstOf(op)) amb('static', true, .25);   // помехи фоном на весь кадр; с once или cut — один удар помех в свою секунду (обрыв сигнала ровно с картинкой)
    // стук по стеклу: если Катя стучит в кадре, звук даёт её рука (синхронно), а не таблица
    else if (n === 'knock' || n === 'glass_tap') trig.push({ at, fn: () => { if (!(beat.img && lastDrawn === beat.img && katyaKnocking())) { play(n); if (n === 'knock') buzz(40); } } });
    else { play(n, Object.assign({ when: at + P }, op)); if (n === 'door') setTimeout(() => buzz(40), (at + P) * 1000); }
  }
  for (const it of sched) { if (isVoice(it.k) && !BUF[it.k] && AC) { lateVoice(it, P); continue; }
    for (const vo of voiceFx(it.k)) { const o = play(it.k, Object.assign({ bus: 'voice' }, vo, { when: it.t + P + (vo.dt || 0) })); if (o) o.voice = true; } }
  if (beat.fx !== 'static' && !(beat.sfx || []).some(s => s[0] === 'static' && !burstOf(s[2]))) amb('static', beat.fx === 'static', .3);
  if (scareAt != null) {
    const sa = scareAt + P;
    // не «бум» из ужастика для детей: нарастание обрывается в тишину, а потом — близкий сухой звук рядом с ухом
    if (beat.fx === 'lunge') {
      // крик в несколько слоёв: сам крик + он же на октаву ниже (утробный рык) + визг выше и чуть сбоку + хруст шеи перед броском + удар по низам
      const J = BUF.jumpscare ? 'jumpscare' : 'scare', D = directG ? [[directG, 1]] : null;
      play('breath_close', { when: sa - 1.0, gain: .9 }); play('neck_crack', { when: sa - .42, gain: .8 });
      play(J, { when: sa, gain: calm() ? .8 : .95 });
      if (!calm()) { play(J, { when: sa + .01, gain: .55, rate: .55, to: D }); play(J, { when: sa + .03, gain: .28, rate: 1.45, pan: -.5, to: D }); play('static', { when: sa, gain: .5, cut: .6 }); play('boom', { when: sa, gain: .9 }); }
    }
    else if (sa > 1.6 && BUF.reverse_swell) play('reverse_swell', { when: sa - BUF.reverse_swell.duration, gain: .5 });
    const hits = beat.hit || (sceneId.startsWith('over_') ? ['neck_crack', 'breath_close'] : ['breath_close']);
    if (beat.fx !== 'lunge') hits.forEach((h, i) => play(BUF[h] ? h : 'scare', { when: sa + i * .12, gain: i ? .7 : 1 }));
    if (hits.includes('glass_slam')) slamHitT = now() + sa;
    if (BUF.sub_presence) play('sub_presence', { when: sa, gain: .8, cut: 2.5 });
  }
  // смерть: в миг обрыва — скример, крик в несколько слоёв (сам крик, утробно на октаву ниже, визг сбоку) и удар по низам
  // (владелец: «скримеры со страшными звуками, а то во всех смертях не ясно, что это смерть»)
  if (sceneId.startsWith('over_') && beat.black != null) {
    const da = beat.black + P, J = BUF.jumpscare ? 'jumpscare' : 'scare', D = directG ? [[directG, 1]] : null;
    play(J, { when: da, gain: calm() ? .8 : 1 }); play(J, { when: da + .01, gain: .55, rate: .55, to: D }); play(J, { when: da + .03, gain: .3, rate: 1.45, pan: -.5, to: D });
    if (BUF.boom) play('boom', { when: da, gain: .9 }); play('static', { when: da, gain: .45, cut: .5 });
    const gk = (goreKind(sceneId) || '').replace(/[LDF]$/, ''), g0 = da + JUMP_D, S = (n, dt, gain, o) => { if (BUF[n]) play(n, Object.assign({ when: g0 + dt, gain }, o)); };   // финал: мокрые, рвущиеся, хрустящие звуки
    if (gk === 'eat' || gk === 'limb' || gk === 'heart') { S('breath_close', .15, .7); S('skin_stretch', .5, 1); S('eat_wet', .6, 1); S('skin_stretch', 1.15, .9, { rate: .85 }); S('eat_wet', 1.22, 1, { rate: .9 }); S('eat_wet', 2.3, .9); S('eat_wet', 2.85, .8, { rate: 1.1 }); }
    else if (gk === 'drag') { S('skin_stretch', .1, .8); S('breath_close', .3, .7); S('eat_wet', .9, .5, { rate: .7 }); S('skin_stretch', 1.8, .7, { rate: .8 }); }
    else if (gk === 'chest') { S('breath_close', .1, .7); S('boom', .5, .7); S('skin_stretch', .5, 1); S('eat_wet', .6, 1); S('eat_wet', 1.25, 1, { rate: .85 }); S('skin_stretch', 1.3, .8); S('eat_wet', 2.2, .7); }
    else if (gk === 'neck' || gk === 'hang') { S('breath_close', .2, .8); S('skin_stretch', .55, .7); S('neck_crack', 1.25, 1); S('neck_crack', 1.32, .75, { rate: .8 }); S('skin_stretch', 1.3, .8); }
    else if (gk) { S('breath_close', .15, .7); S('skin_stretch', .5, 1); S('eat_wet', .55, 1); S('eat_wet', 1.1, .9, { rate: .9 }); S('breath_close', 1.6, .6, { rate: .7 }); S('eat_wet', 2.0, .8); }
  }
}
function gapTime(k) {   // начало самой длинной паузы между словами (там скример)
  const w = (TIM[k] || {}).w || []; let best = 0, at = durOf(k) / 2;
  for (let i = 1; i < w.length; i++) { const gp = w[i][0] - w[i - 1][0]; if (gp > best) { best = gp; at = w[i][0] - .15; } }
  return at;
}
function skipBeat() { if (mode !== 'play' || !beat) return; stopAll(); duck(false); waiting = false; tapEl.classList.add('hide'); nextBeat(); }
// тап по экрану: начать кадр / дочитать реплику / дальше
function tap() { if (userPause) return;   // на паузе тап по игре не проходит
  if (mode !== 'play' || !beat) return;
  if (waiting) return beginBeat();
  if (holding) return nextBeat();
  const t = now() - bT0;
  if (t < 0) return;                                         // тишина перед ударом и «соединение потеряно» не пропускаются
  if (scareAt != null && t < scareAt + 1) return;            // скример не пропускается
  for (const o of [...playing]) if (o.voice) { try { o.s.stop(); } catch {} playing.delete(o); }
  dropPending();
  for (const e of trig) if (!e.done && !e.carry && e.at > t) e.done = true;   // отложенные звуки кадра больше не нужны (смс покажем сразу)
  for (const m of [].concat(beat.sms || [])) if ((m.at ?? .5) > t) showSms(m);
  bT0 = now() - bDur;                                        // сразу к концу кадра (реплика показана целиком)
}
// записка «Правила» (📄 в игре): памятка охранника + записи Фрэнка, которые уже видел (храним место в сценарии — текст берём на текущем языке)
const known = new Set(LS.get('rules2', []));
function learnRule() { const k = sceneId + ':' + bi; if (!known.has(k)) { known.add(k); LS.set('rules2', [...known]); } }
function showRules() {
  const memo = (S.shift.memo || []).map(tr), seen = [...known].map(k => { const [sc, i] = k.split(':'); const b = ((S.scenes[sc] || {}).beats || [])[+i]; return b && b.rule ? tr(b.rule) : null; }).filter(Boolean);
  noteEl.innerHTML = `<div class="memo"><b>${memo[0] || ''}</b>${memo.slice(1).map(m => `<span>${escH(m)}</span>`).join('')}${[...new Set(seen)].map(r => `<i>${escH(r)}</i>`).join('')}<small>${tr('нажми, чтобы закрыть')}</small></div>`;
  noteEl.classList.remove('hide'); noteEl.onclick = () => noteEl.classList.add('hide');
}

/* ---------- смс: уведомление на экране блокировки, сверху ---------- */
let smsTimer = 0;
function showSms(m) {
  if (!smsEl || !m) return;
  smsEl.innerHTML = `<div class="si">${ICON.msg}</div><div class="sb"><div class="sh"><span>${tr('СООБЩЕНИЯ')}</span><span>${tr('сейчас')}</span></div><b>${escH(m.from || tr('Лили'))}</b><p>${escH(m.text || '')}</p></div>`;
  smsEl.classList.remove('hide', 'out', 'in'); void smsEl.offsetWidth; smsEl.classList.add('in');
  play('buzz', { gain: .6 }); buzz([90, 70, 90]);
  clearTimeout(smsTimer); smsTimer = setTimeout(() => { smsEl.classList.add('out'); smsTimer = setTimeout(() => smsEl.classList.add('hide'), 450); }, (m.dur || 3.5) * 1000);
}
function hideSms() { clearTimeout(smsTimer); if (smsEl) smsEl.classList.add('hide'); }

/* ---------- «соединение потеряно»: звук обрывается в настоящую тишину, потом — жёсткая склейка ---------- */
function glitchStart() {
  if (!AC) return; const t = AC.currentTime;
  play('static', { cut: .09, gain: .55 }); buzz(30);
  for (const n of [bedG, musicG]) if (n) { n.gain.cancelScheduledValues(t); n.gain.setValueAtTime(0, t + .09); }
  if (bed) bed.lv = -1;
}
function glitchEnd() {
  if (!AC) return; const t = AC.currentTime;
  if (musicG) { musicG.gain.cancelScheduledValues(t); musicG.gain.setValueAtTime(hushOn ? .012 : .55, t); }
  if (bedG) { bedG.gain.cancelScheduledValues(t); bedG.gain.setValueAtTime(hushOn ? .045 : bedWant.lv, t); if (bed) bed.lv = hushOn ? .045 : bedWant.lv; }
  play('static', { cut: .22, gain: .45 });
}

/* ---------- что дальше ---------- */
function runThen(th) {
  voiceReady(objKeys(th));
  dlgEl.classList.add('hide'); tapEl.classList.add('hide'); chapEl.classList.add('hide'); ruleEl.classList.add('hide'); amb('static', false); amb('heart', false);
  if (hushOn && !th.choice) hush(false);   // тишина перед скримером не тянется в смену, мини-игру, концовку (у выбора — пусть держится)
  if (th.goto) return startScene(th.goto);
  if (th.choice) return showChoice(th);
  if (th.camgame) return camGame(th.camgame);
  if (th.shift) return shiftGame(th);
  if (th.mg) return MG[th.mg](th);
  if (th.ending) return th.fin ? endScreen(th) : finale(th);
  if (th.over) return overScreen(th);
  if (th.demo) return demoEnd();
  if (th.explore) return showAct(th.explore, 'explore');
  if (th.talk) return showAct(th.talk, 'talk');
  if (th.back) return backTo(th.back);
}
/* ---------- обход и разговор: точки на кадре (explore) и темы (talk) — каждая ведёт в свою короткую сцену,
   та кончается then: { back: 'сцена' } и возвращает сюда. Осмотренное помнится флагом v:сцена (и после перезагрузки) ---------- */
let act = null;
const actItems = th => (th.spots || th.topics || []).map((s, i) => ({ s, i })).filter(({ s }) => flagOk(s.if) && !(s.once !== false && flags['v:' + s.go]));
function backTo(id) { const sc = S.scenes[id]; if (!sc) return menu(); sceneId = id; scene = sc; LS.set('save', id); LS.set('saveT', lastTime); runThen(sc.then); }
function showAct(th, kind) {
  mode = kind; const my = gen, items = actItems(th), lv = th.leave || th.bye;
  act = { kind, th, t0: now(), cams: th.cams || [[.5, .5, 1, 1.03]], items };
  voiceReady(items.flatMap(it => sceneKeys(S.scenes[it.s.go])).concat(lv && lv.go ? sceneKeys(S.scenes[lv.go]) : []));   // реплики точек и тем — заранее, чтобы переход не ждал
  const leaveOk = !lv || (lv.need || []).every(k => flags['v:' + k]);
  const go = (to, set) => { if (my !== gen || !act) return; play('click'); act = null; actEl.innerHTML = ''; actEl.classList.add('hide'); setFlags(set); startScene(to); };
  if (kind === 'talk') {
    actEl.innerHTML = `<div class="talk">${th.title ? `<h2>${tr(th.title)}</h2>` : ''}${items.map(({ s, i }, n) => `<button data-i="${i}"><b>${n + 1}</b>${tr(s.t)}</button>`).join('')}
      ${lv && leaveOk ? `<button class="bye" data-i="bye"><b>${items.length + 1}</b>${tr(lv.t)}</button>` : ''}</div>`;
  } else {
    actEl.innerHTML = `<div class="ex-hint">${tr(th.hint || 'Осмотрись. Нажми на то, что хочешь проверить.')}</div>
      ${items.map(({ s, i }) => `<button class="spot" data-i="${i}"><i></i><span>${tr(s.t)}</span></button>`).join('')}
      ${lv && leaveOk ? `<button class="btn ghost ex-leave" data-i="bye">${tr(lv.t)}</button>` : ''}`;
  }
  actEl.classList.remove('hide');
  actEl.querySelectorAll('button').forEach(b => b.onclick = e => { e.stopPropagation(); const i = b.dataset.i;
    if (i === 'bye') return go(lv.go, lv.set);
    const s = (th.spots || th.topics)[+i]; setFlags('v:' + s.go); go(s.go, s.set); });
  if (kind === 'explore' && !items.length && lv) return go(lv.go, lv.set);   // всё осмотрено — дальше сам
}
// точки обхода стоят на предметах кадра: мировая точка → место на экране (каждый кадр, камера дышит)
function actSpots() {
  if (!act || act.kind !== 'explore' || !window.World || !World.project) return;
  const bs = [...actEl.querySelectorAll('.spot')];
  bs.forEach(b => { const s = act.th.spots[+b.dataset.i], q = s.at ? World.project(s.at) : null;   // кольцо — на самом предмете, подпись под ним
    const [x, y] = q && q[2] ? q : s.xy || [.5, .5]; b.style.left = (x * 100).toFixed(2) + '%'; b.style.top = (y * 100).toFixed(2) + '%'; b.style.visibility = 'visible'; });
  if (act.laid) return; act.laid = true;
  // подписи раскладываются один раз: под кольцом или над ним — где меньше наезжает на чужие кольца, подписи, подсказку и кнопку выхода; у края экрана подпись сдвигается внутрь
  const box = actEl.getBoundingClientRect(), rc = e => e.getBoundingClientRect(), rings = bs.map(b => rc(b.querySelector('i')));
  const ov = (a, c) => Math.max(0, Math.min(a.right, c.right) - Math.max(a.left, c.left)) * Math.max(0, Math.min(a.bottom, c.bottom) - Math.max(a.top, c.top));
  const busy = [...actEl.querySelectorAll('.ex-hint, .ex-leave')].map(rc), sps = bs.map(b => b.querySelector('span'));
  const fit = sp => { sp.style.transform = ''; const L = rc(sp), dx = L.left < box.left + 6 ? box.left + 6 - L.left : L.right > box.right - 6 ? box.right - 6 - L.right : 0; if (dx) sp.style.transform = `translateX(${dx.toFixed(1)}px)`; };
  const cost = () => { sps.forEach(fit); const S = sps.map(rc); let o = 0;
    S.forEach((a, i) => { if (a.top < box.top || a.bottom > box.bottom) o += 1e6; rings.forEach((r, j) => { if (j !== i) o += ov(a, r); }); for (const B of busy) o += ov(a, B); for (let j = i + 1; j < S.length; j++) o += ov(a, S[j]); });
    return o; };
  let best = 0, bc = Infinity;   // точек немного — перебираем все раскладки (над/под) и берём ту, где меньше всего наезжает
  for (let m = 0; m < 1 << Math.min(bs.length, 6); m++) { bs.forEach((b, i) => b.classList.toggle('up', !!(m >> i & 1))); const c = cost(); if (c < bc - .5) { bc = c; best = m; } }
  bs.forEach((b, i) => b.classList.toggle('up', !!(best >> i & 1))); sps.forEach(fit);
}
function showChoice(th) {
  mode = 'choice'; music('tension');
  const vis = th.opts.map((o, i) => [o, i]).filter(([o]) => flagOk(o.if));   // вариант может зависеть от того, что ты узнал раньше
  choiceEl.innerHTML = `<h2>${th.choice}</h2>` + vis.map(([o, i], n) => `<button data-i="${i}"><b>${n + 1}</b>${o.t}</button>`).join('') + (th.timeout ? '<div id="timer"><i></i></div>' : '');
  choiceEl.classList.remove('hide');
  let done = false, t0 = now(), lastTick = -1; const my = gen;
  const pick = i => { if (done || my !== gen) return; done = true; play('click'); choiceEl.classList.add('hide'); const o = th.opts[i];
    setFlags(o.set); if (!/^over_/.test(o.go)) runPick(sceneId, th.choice, o.t, i); startScene(o.go); };
  choiceEl.querySelectorAll('button').forEach(b => b.onclick = () => pick(+b.dataset.i));
  if (th.timeout) {
    const bar = choiceEl.querySelector('#timer i');
    (function loop() {
      if (done || my !== gen) return; const left = th.timeout - (now() - t0);
      bar.style.width = Math.max(0, left / th.timeout * 100) + '%';
      const s = Math.ceil(left); if (s <= 3 && s !== lastTick && s > 0) { lastTick = s; play('tick', { gain: 2 }); }
      if (left <= 0) return pick(th.def != null && flagOk(th.opts[th.def].if) ? th.def : vis[Math.floor(Math.random() * vis.length)][1]); requestAnimationFrame(loop);
    })();
  }
}

/* ---------- мини-игра: обход камер ---------- */
let camView = null;
function camGame(next) {
  mode = 'cams'; music('drone'); amb('static', true, .08);
  let seenCams = new Set(), warned = false; const my = gen;
  const draw = () => {
    mgEl.innerHTML = `<div class="mghead">${tr('ОБХОД КАМЕР')}<small>${tr('Проверь три камеры. В тринадцатую не смотри.')}</small></div><div class="camgrid">` +
      S.cams.map((c, i) => c.forbidden ? `<div class="cam off" data-i="${i}"><b>${c.id}</b><span>${tr('ВЫКЛ')}</span></div>`
        : `<div class="cam ${seenCams.has(i) ? 'seen' : ''}" data-i="${i}" style="background-image:url(${window.World ? World.thumb(c.img, { cx: c.c[0], cy: c.c[1], z: c.c[2], cctv: 1 }) : ''})"><b>${c.id} ${c.name}</b></div>`).join('') + '</div>' +
      `<div class="mgfoot">${seenCams.size >= 3 ? `<button class="btn" id="cgo" style="text-align:center">${tr('Продолжить смену')}</button>` : `<div style="text-align:center;opacity:.7;font-size:calc(3.6 * var(--u))">${trf('Проверено: {n} из 3', { n: seenCams.size })}</div>`}</div>`;
    mgEl.querySelectorAll('.cam').forEach(el => el.onclick = () => {
      const i = +el.dataset.i, c = S.cams[i];
      if (c.forbidden) {
        if (!warned) { warned = true; play('static'); if (!calm()) { mgEl.classList.add('flash'); setTimeout(() => mgEl.classList.remove('flash'), 300); }
          el.innerHTML = `<b>CAM 13</b><span style="font-size:calc(3.6 * var(--u))">${tr('ТОЧНО ВКЛЮЧИТЬ?')}<br>${tr('«НЕ СМОТРИ В НЕЁ»')}</span>`; return; }
        amb('static', false); mgEl.classList.add('hide'); return startScene(c.over || 'over_cam13');
      }
      play('click'); play('static', { gain: .5 });
      camView = { c, t0: now() }; mgEl.classList.add('hide');
      lastSubKey = ''; let hold = 2600;
      if (c.say) { const k = c.say, s = (TIM[k] && TIM[k].t) || ''; play(k, { bus: 'voice' }); say(whoOf(k), s, s.length); hold = Math.max(hold, durOf(k) * 1000 + 700); }   // голос Итана
      else say('КАМЕРА', c.note, c.note.length);
      if (c.jump && !c.jumped) { c.jumped = true; camView.jump = true; hush(true, true);
        setTimeout(() => { if (my !== gen) return; hush(false); play(BUF.jumpscare ? 'jumpscare' : 'scare', { gain: .8 }); buzz([200, 40, 300]); shakeAmp = 40; }, 1250); }
      setTimeout(() => { if (my !== gen) return; camView = null; dlgEl.classList.add('hide'); seenCams.add(i); if (c.creep) play('boom', { gain: .4 }); mgEl.classList.remove('hide'); draw(); }, camView.jump ? 3300 : hold);
    });
    const go = $('cgo'); if (go) go.onclick = () => { play('click'); amb('static', false); startScene(next); };
  };
  mgEl.classList.remove('hide'); draw();
}

/* ---------- мини-игры второго акта ---------- */
const MG = {
  // держать кнопку (не дышать), отпустить можно только когда шаги стихнут
  hold(th) {
    mode = 'mg'; music('tension'); amb('heart', true, .7); const my = gen;
    mgEl.innerHTML = `<div class="mghead" style="top:10%">${th.title}<small>${th.hint}</small></div>${th.calls ? '<div id="mgSub" style="position:absolute;left:7%;right:7%;top:31%;text-align:center;font-size:calc(3.8 * var(--u));font-weight:600;font-style:italic;line-height:1.35;color:#ff6a6a;text-shadow:0 0 calc(2 * var(--u)) #000"></div>' : ''}<div class="mgfoot" style="bottom:72%"><div class="meter"><i id="air"></i></div><div style="text-align:center;font-size:calc(3.4 * var(--u));margin-top:calc(1.5 * var(--u));opacity:.7">${th.meter || tr('воздух')}</div></div><button class="bigbtn" id="hb">${th.btn || tr('ДЕРЖИ')}</button>`;   // надпись — как у отпущенной кнопки (было «ДЕРЖИ» по-русски на любом языке)
    mgEl.classList.remove('hide');   // заголовок ниже обычного (10%): на вертикальном экране сверху кнопки меню и звука — «ДЕРЖИ ФРЭНКА» уходило под них; строка голоса — между шкалой и кнопкой
    const btn = $('hb'), air = $('air'); let held = false, a = 1, t0 = now(), lt = t0, safe = false, end = false, steps = 0;
    const dur = th.dur || 9, set = h => { held = h; btn.classList.toggle('held', h); btn.textContent = h ? (th.btnOn || tr('НЕ ДЫШИ')) : (th.btn || tr('ДЕРЖИ')); };
    btn.onpointerdown = e => { e.preventDefault(); try { btn.setPointerCapture(e.pointerId); } catch {} set(true); }; btn.onpointerup = btn.onpointerleave = btn.onpointercancel = () => set(false);   // захват: нажатая кнопка сжимается (scale .93) — мышь у края «уходила» с неё, и это считалось «отпустил»
    const fin = ok => { if (end || my !== gen) return; end = true; amb('heart', false); mgEl.classList.add('hide'); startScene(ok ? th.win : th.lose); };
    (function loop() {
      if (end || my !== gen) return; const t = now() - t0, fdt = Math.min(.1, now() - lt); lt = now();
      // босые шаги кружат вокруг будки: то слева, то справа
      // вместо шагов — голос из темноты (держать Фрэнка: оно зовёт его голосом матери)
      if (th.calls && t > 1 && Math.floor(t / 2.6) > steps && t < dur) { steps++; const k = th.calls[steps % th.calls.length]; play(k, { bus: 'voice', gain: .9, pan: Math.sin(t) * .6 }); const sb = $('mgSub'); if (sb && TIM[k]) sb.textContent = TIM[k].t; }   // что зовёт голос — и словами (субтитры остаются всегда, и без озвучки)
      else if (!th.calls && t > .8 && Math.floor(t / 1.1) > steps && t < dur) { steps++; const pan = Math.sin(t * 1.3) * .8; if (!play('steps_barefoot', { off: Math.random() * 4, cut: 1, gain: .6 + .4 * Math.sin(t), pan })) play('steps', { gain: .5 + .5 * Math.sin(t), pan }); if (steps % 3 === 0) play(BUF.breath_close ? 'breath_close' : 'ch_whisper', { gain: .45, pan: -pan * .6 }); }
      a = Math.max(0, Math.min(1, a + (held ? -Math.min(.085, .8 / dur) : 0.25) * fdt)); air.style.width = a * 100 + '%';   // воздуха (сил) хватает на весь срок и ещё секунды на три после «ТИХО…»: «ДЕРЖИ ФРЭНКА» (12 с) при .085/с кончалось на 11,8 с — схватил сразу и держал — всё равно смерть; при .93/срок после «ТИХО…» оставалась секунда — не успевал отпустить
      if (t > 2.5 && t < dur && !held) return fin(false);      // выдохнул — услышала
      if (a <= 0) return fin(false);                            // задохнулся
      if (t >= dur && !safe) { safe = true; mgEl.querySelector('.mghead').innerHTML = (th.safe || tr('ТИХО…')) + '<small>' + (th.safeHint || tr('Шаги ушли. Можно дышать.')) + '</small>'; }
      if (safe && !held) return fin(true);
      requestAnimationFrame(loop);
    })();
  },
  // держать дверь: часто жать, полоса не должна упасть до нуля
  door(th) {
    mode = 'mg'; music('tension'); const my = gen;
    mgEl.innerHTML = `<div class="mghead">${th.title}<small>${th.hint}</small></div><div class="mgfoot" style="bottom:72%"><div class="meter"><i id="dr"></i></div></div><button class="bigbtn" id="db">${tr('ДЕРЖИ')}<br>${tr('ДВЕРЬ')}</button>`;
    mgEl.classList.remove('hide');
    let v = .6, t0 = now(), lt = t0, end = false, nextHit = .5; const bar = $('dr');
    $('db').onpointerdown = e => { e.preventDefault(); v = Math.min(1, v + .075); shakeAmp = 10; };
    const fin = ok => { if (end || my !== gen) return; end = true; mgEl.classList.add('hide'); startScene(ok ? th.win : th.lose); };
    (function loop() {
      if (end || my !== gen) return; const t = now() - t0;
      if (t > nextHit) { if (!play('door_slams', { off: Math.random() * 3, cut: .7, gain: 1.1 })) play('knock', { gain: 1.2 }); shakeAmp = 26; buzz(50); nextHit = t + .9 + Math.random() * .6; if (Math.random() < .3) play('ch_katya', { bus: 'voice', gain: .5 }); }
      const fdt = Math.min(.1, now() - lt); lt = now(); v -= (.0042 + t * .00022) * 60 * fdt; bar.style.width = Math.max(0, v * 100) + '%';
      if (v <= 0) return fin(false); if (t > (th.dur || 12)) return fin(true);
      requestAnimationFrame(loop);
    })();
  },
  // выдернуть шнур монитора: потянуть вилку вниз, пока он не подошёл к камере
  cord(th) {
    mode = 'mg'; music('tension'); amb('static', true, .45); const my = gen, dur = th.dur || 3.2;
    mgView = { img: th.img || 'cam13_monster', c: th.cam || [.54, .34, 1.2, 3.4], t0: now(), dur, label: th.label || 'CAM 13' };
    mgEl.innerHTML = `<div class="mghead low">${th.title || tr('ВЫДЕРНИ ШНУР')}<small>${th.hint || tr('Тяни вилку вниз, быстро!')}</small></div><div class="cord"><div class="wire"></div><div class="plug" id="plug">${ICON.plug}</div></div><div class="mgfoot" style="bottom:8%"><div class="meter"><i id="ct" style="width:100%"></i></div></div>`;
    mgEl.classList.remove('hide'); play('ch_monster', { bus: 'voice', gain: .8 }); buzz(80);
    const plug = $('plug'), box = mgEl.querySelector('.cord'), t0 = now(); let y0 = null, end = false;
    const fin = ok => { if (end || my !== gen) return; end = true; amb('static', false); if (ok) { play('click'); play('boom'); } mgEl.classList.add('hide'); mgView = null; startScene(ok ? th.win : th.lose); };
    plug.onpointerdown = e => { e.preventDefault(); y0 = e.clientY; try { plug.setPointerCapture(e.pointerId); } catch {} };
    plug.onpointermove = e => { if (y0 == null) return; const d = Math.max(0, e.clientY - y0), lim = box.clientHeight * .45; plug.style.transform = `translateY(${d}px)`; if (d > lim) fin(true); };
    plug.onpointerup = plug.onpointercancel = () => { if (end) return; y0 = null; plug.style.transform = ''; };
    (function loop() { if (end || my !== gen) return; const l = dur - (now() - t0); const el = $('ct'); if (el) el.style.width = Math.max(0, l / dur * 100) + '%'; if (Math.random() < .08) shakeAmp = 12; if (l <= 0) return fin(false); requestAnimationFrame(loop); })();
    window.__ns.cordWin = () => fin(true);
  },
  // бег: быстро выбрать сторону (подсказка — где гаснет свет, туда нельзя)
  run(th) {
    mode = 'mg'; music('tension'); const my = gen; let k = 0, end = false; const N = th.n || 5; mgView = null;
    const fin = ok => { if (end || my !== gen) return; end = true; mgEl.classList.add('hide'); mgView = null; startScene(ok ? th.win : th.lose); };
    const step = () => {
      if (k >= N) return fin(true);
      const bad = Math.random() < .5 ? 'L' : 'R';
      mgView = { img: 'run_lit', c: [.5, .5, 1, 1.04], t0: mgView ? mgView.t0 : now(), dur: 1e9, run: bad };
      mgEl.innerHTML = `<div class="mghead">${th.title}<small>${tr(bad === 'L' ? 'Свет гаснет СЛЕВА — беги ВПРАВО →' : 'Свет гаснет СПРАВА — беги ← ВЛЕВО')} (${k + 1}/${N})</small></div><div class="mgfoot" style="bottom:72%"><div class="meter"><i id="rt" style="width:100%"></i></div></div><div class="arrows"><button data-d="L" aria-label="${tr('Влево')}">${ICON.left}</button><button data-d="R" aria-label="${tr('Вправо')}">${ICON.right}</button></div>`;
      mgEl.classList.remove('hide'); play('steps', { gain: .8 }); shakeAmp = 14;
      const t0 = now(), lim = Math.max(1.3, 2.6 - k * .2); let ok = false;
      mgEl.querySelectorAll('.arrows button').forEach(b => b.onclick = () => { if (ok) return; ok = true; if (b.dataset.d === bad) return fin(false); play('click'); k++; step(); });
      (function loop() { if (end || ok || my !== gen) return; const l = lim - (now() - t0); const el = $('rt'); if (el) el.style.width = Math.max(0, l / lim * 100) + '%'; if (l <= 0) return fin(false); requestAnimationFrame(loop); })();
    };
    step();
  },
  // запотевшее стекло: протереть пальцем; когда видно больше половины — в стекло бьёт ладонь
  wipe(th) {
    mode = 'mg'; music('tension'); amb('heart', true, .35); const my = gen, t0 = now(), FW = LAND ? 192 : 108, FH = LAND ? 108 : 192;
    const F = document.createElement('canvas'); F.width = FW; F.height = FH; const f = F.getContext('2d');
    // пелена гуще к краям, в ней капельки; несколько подтёков — по ним чуть видно, что за стеклом
    const rg = f.createRadialGradient(FW / 2, FH * .45, 10, FW / 2, FH * .45, FH * .62); rg.addColorStop(0, 'rgba(150,162,172,.93)'); rg.addColorStop(1, 'rgba(120,130,140,.985)');   // плотная пелена: за стеклом не видно, кто там, пока не протрёшь f.fillStyle = rg; f.fillRect(0, 0, FW, FH);
    for (let i = 0; i < 260; i++) { f.fillStyle = `rgba(${200 + Math.random() * 50},${210 + Math.random() * 40},230,${.08 + Math.random() * .16})`; f.beginPath(); f.arc(Math.random() * FW, Math.random() * FH, .4 + Math.random() * 1.3, 0, 7); f.fill(); }
    f.globalCompositeOperation = 'destination-out';
    for (let i = 0; i < 7; i++) { let x = Math.random() * FW, y = Math.random() * 60; const n = 30 + Math.random() * 60; f.fillStyle = 'rgba(0,0,0,.22)';
      for (let j = 0; j < n; j++) { x += Math.random() * .8 - .4; y += 1.3; f.fillRect(x, y, 1.1, 1.4); } f.beginPath(); f.arc(x, y + 1, 1.5, 0, 7); f.fill(); }
    f.globalCompositeOperation = 'source-over';
    mgView = { img: th.img || 'katya_slam', c: th.cam || [.5, .47, 1.22, 1.22], t0, dur: 1e9, tAt: .1, fog: F };
    mgEl.innerHTML = `<div class="mghead">${th.title}<small>${th.hint}</small></div>`;
    mgEl.classList.remove('hide');
    let last = null, end = false, slam = 0, chk = 0, sq = 0, clear = 0;
    const pt = e => { const r = cv.getBoundingClientRect(); return [(e.clientX - r.left) / r.width * FW, (e.clientY - r.top) / r.height * FH]; };
    const rub = (a, b) => { f.globalCompositeOperation = 'destination-out'; f.strokeStyle = 'rgba(0,0,0,.8)'; f.lineCap = 'round'; f.lineWidth = 19; f.beginPath(); f.moveTo(a[0], a[1]); f.lineTo(b[0], b[1]); f.stroke(); f.globalCompositeOperation = 'source-over'; };
    mgEl.onpointerdown = e => { e.preventDefault(); if (slam) return; last = pt(e); rub(last, last); };
    mgEl.onpointermove = e => { if (!last || slam) return; const p = pt(e); rub(last, p); if (Math.hypot(p[0] - last[0], p[1] - last[1]) > 5 && now() - sq > .16) { sq = now(); play('glass_tap', { gain: .12, rate: 1.7 + Math.random() * .4, cut: .12 }); } last = p; };
    mgEl.onpointerup = mgEl.onpointercancel = () => { last = null; };
    const fin = () => { if (end || my !== gen) return; end = true; amb('heart', false); mgEl.onpointerdown = mgEl.onpointermove = mgEl.onpointerup = mgEl.onpointercancel = null; mgEl.classList.add('hide'); mgView = null; startScene(th.win); };
    (function loop() {
      if (end || my !== gen) return; const t = now() - t0;
      if (!slam) {
        f.fillStyle = 'rgba(140,152,162,.006)'; f.fillRect(0, 0, FW, FH);   // стекло снова потеет от дыхания с той стороны
        if (now() - chk > .25) { chk = now(); const d = f.getImageData(0, 0, FW, FH).data; let n = 0; for (let i = 3; i < d.length; i += 16) if (d[i] < 90) n++; clear = n / (d.length / 16); }
        if (clear > (th.need || .45) || t > (th.dur || 15)) {   // увидел пустую улицу — и тут же удар в стекло
          slam = now(); mgView.tAt = null; mgView.st = .1 - t; mgEl.querySelector('.mghead').classList.add('hide');
          if (th.slam) setTimeout(() => { if (my === gen && mgView) { mgView.img = th.slam; mgView.st = -(now() - t0) + .02; } }, 140);   // за протёртым стеклом было пусто — и вдруг она, ладони в стекло
          setTimeout(() => { if (my !== gen) return; play('glass_slam', { gain: 1.1 }); play('breath_close', { gain: .7, when: .12 }); shakeAmp = 34; buzz([60, 30, 120]); if (!calm()) blackT = now(); }, 150);
        }
      } else if (now() - slam > (th.after || 1.7)) return fin();
      requestAnimationFrame(loop);
    })();
  },
  // фонарик: луч ведёт палец; в темноте кто-то стоит — посветишь на него, и его уже нет; потом найти дверь
  torch(th) {
    mode = 'mg'; music('tension'); amb('heart', true, .3); const my = gen, t0 = now();
    const L = { x: .3, y: .9 }, tgt = { x: .3, y: .9 };
    mgView = { img: th.img, c: th.cam || [.5, .5, 1, 1], t0, dur: 1e9, torch: L };
    mgEl.innerHTML = `<div class="mghead">${th.title}<small id="th">${th.hint}</small></div>`;
    mgEl.classList.remove('hide'); play('click');
    let end = false, seen = th.mon ? 0 : 1, onMon = 0, onGoal = 0, gone = !th.mon, found = 0, jump = 0, hitT = 0, lt = now();   // без th.mon — сразу ищем цель
    const pt = e => { const r = cv.getBoundingClientRect(); tgt.x = clamp((e.clientX - r.left) / r.width, 0, 1); tgt.y = clamp((e.clientY - r.top) / r.height - .07, 0, 1); };   // луч чуть выше пальца — палец не закрывает
    mgEl.onpointerdown = e => { e.preventDefault(); if (!found) pt(e); }; mgEl.onpointermove = e => { if (!found && (e.buttons || e.pointerType === 'touch')) pt(e); };
    const near = (p, r) => Math.hypot((L.x - scrX(p[0])) * W / H, L.y - p[1]) < r;   // расстояние в долях высоты экрана
    const fin = () => { if (end || my !== gen) return; end = true; amb('heart', false); mgEl.onpointerdown = mgEl.onpointermove = null; mgEl.classList.add('hide'); mgView = null; startScene(th.win); };
    (function loop() {
      if (end || my !== gen) return; const dt = Math.min(.1, now() - lt); lt = now();
      const kf = Math.min(1, dt * (jump ? 22 : 9)); L.x += (tgt.x - L.x) * kf; L.y += (tgt.y - L.y) * kf;   // фонарь тяжёлый — луч догоняет палец
      if (!seen) { onMon = near(th.mon, .06) ? onMon + dt : 0;
        if (onMon > .25) { seen = now(); play('breath_close', { gain: .9 }); play('whispers', { gain: .35, cut: 1.4 }); shakeAmp = 10; buzz([40, 30, 80]); } }
      else if (!gone && now() - seen > .75) { gone = true; mgView.img = th.img2; blackT = now(); play('fluor_flicker', { gain: .4, cut: .3 }); $('th').textContent = th.hint2; }
      if (gone && !found) { onGoal = near(th.goal, .05) ? onGoal + dt : 0; if (onGoal > .7) { found = now(); play('click'); $('th').textContent = th.found; } }
      if (found && th.img3) { const e = now() - found;   // за спиной вздох — луч сам дёргается влево, а он стоит вплотную
        if (e > .9 && !jump) { jump = now(); mgView.img = th.img3; mgView.st = -(jump - mgView.t0); tgt.x = scrX(th.near[0]); tgt.y = th.near[1]; play('breath_close', { gain: 1 }); mgEl.querySelector('.mghead').classList.add('hide'); }
        if (jump && now() - jump > .45 && !hitT) { hitT = now(); play(BUF.jumpscare ? 'jumpscare' : 'scare', { gain: calm() ? .7 : .95 }); shakeAmp = 40; buzz([200, 40, 300]); }
        if (hitT && now() - hitT > .55) { blackT = now() + .4; return fin(); } }
      else if (found && now() - found > .8) return fin();
      requestAnimationFrame(loop);
    })();
  },
  // перемотка записи камеры: ползунок влево — запись идёт назад; нашёл, где он ещё жив, — и запись сама играет дальше
  tape(th) {
    mode = 'mg'; music('tension'); amb('static', true, .22); const my = gen, len = th.len || 5.6, base = th.clock0 || [3, 0, 0];
    const clockAt = tt => { const x = base[0] * 3600 + base[1] * 60 + base[2] + Math.floor(tt); return [Math.floor(x / 3600), Math.floor(x / 60) % 60, x % 60].map(v => String(v).padStart(2, '0')).join(':'); };
    mgView = { img: th.img, c: th.cam || [.47, .56, 1.9, 1.9], t0: now(), dur: 1e9, tAt: len, label: th.label, rw: 0, rwHud: { tag: tr('◀◀ ЗАПИСЬ'), clock: clockAt(len) } };
    mgEl.innerHTML = `<div class="mghead cam">${th.title}<small id="th">${th.hint}</small></div><div class="tape" id="tape"><div class="tbar"><i id="tf"></i></div><div class="tknob" id="tk">${ICON.left}</div></div>`;
    mgEl.classList.remove('hide'); play('click');
    const box = $('tape'), knob = $('tk'), fill = $('tf'); let drag = false, end = false, lastTick = len, found = 0;
    const setK = k => { k = clamp(k, 0, 1); const tt = k * len; mgView.rw = Math.min(1, mgView.rw + Math.abs(tt - mgView.tAt) * 1.5); mgView.tAt = tt; mgView.rwHud.clock = clockAt(tt);
      knob.style.left = k * 100 + '%'; fill.style.width = k * 100 + '%';
      if (Math.abs(tt - lastTick) > .18) { lastTick = tt; play('tick', { gain: .16, rate: 1.5 }); }
      if (!found && tt < th.goal) { found = now(); drag = false; box.classList.add('done'); play('click'); $('th').textContent = th.found; mgView.rwHud.tag = tr('▶ ЗАПИСЬ'); } };
    setK(1);
    const move = e => { const r = box.getBoundingClientRect(); setK((e.clientX - r.left) / r.width); };
    box.onpointerdown = e => { if (found) return; e.preventDefault(); drag = true; try { box.setPointerCapture(e.pointerId); } catch {} move(e); };
    box.onpointermove = e => { if (drag && !found) move(e); }; box.onpointerup = box.onpointercancel = () => { drag = false; };
    const fin = () => { if (end || my !== gen) return; end = true; amb('static', false); mgEl.classList.add('hide'); mgView = null; startScene(th.win); };
    let pl = null;
    (function loop() {
      if (end || my !== gen) return;
      if (found) {   // сама играет вперёд с того места, где он ещё жив, — до конца рывка
        const e = now() - found; if (e > .9) { if (pl == null) { pl = mgView.tAt; (th.sfx || []).forEach(([n, at, o]) => play(n, Object.assign({ when: Math.max(0, at - pl) }, o))); } mgView.tAt = pl + (e - .9); mgView.rwHud.clock = clockAt(mgView.tAt); if (mgView.tAt > th.stop) return fin(); }
      }
      requestAnimationFrame(loop);
    })();
  }
};

/* ---------- экраны ---------- */
/* ---------- смена: часы ночи между сюжетными сценами — камеры, пульт, журнал; странное — по правилам ----------
   S.shift.segs[id] = { from, to, cams, cam, events: [{ at, an | say | radio | sms | state | call | task | hint }], rnd: { pool, n }, next }
   S.shift.an[id]   = { cam, kind | kinds, props, fix: 'log' | 'light:N' | 'lift' | 'door' | 'none', win, say, stare, box, sfx }
   Записать — что видно на текущей камере; пульт — свет этажей, вход, лифт. Пропустил три странности — оно приходит к будке. */
const SEC_MIN = 7;   // секунд на игровую минуту (час ≈ 7 минут)
const CAMS = { '01': 'ВХОД', '02': 'ХОЛЛ', '03': 'ЛИФТЫ', '04': 'ЛЕСТНИЦА', '07': 'ЭТАЖ 7', '10': 'ЭТАЖ 10', 'm2': 'ПОДВАЛ', '13': '' };
const camNo = c => c === 'm2' ? '−2' : c, camLabel = c => 'CAM ' + camNo(c) + (CAMS[c] ? '  ' + tr(CAMS[c]) : '');
const KINDS = ['Свет', 'Дверь', 'Лифт', 'Помехи', 'Кто-то', 'Звук', 'Другое'];
const toMin = s => { const [h, m] = s.split(':').map(Number); return (h < 12 ? h + 24 : h) * 60 + m; };
const minStr = v => { const h = Math.floor(v / 60) % 24, m = Math.floor(v) % 60; return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0'); };
let shf = null, shk = null, shSpd = 1;   // смена сейчас; что переходит из часа в час (пульт, записи, кто где); shSpd — ускорение для проверок
function shkNew() { return { door: 0, lift: 0, lights: {}, log: [], st: {}, c13: false, read: false }; }
function shiftGame(th) {
  const seg = S.shift.segs[th.shift]; if (!seg) return startScene(S.start);
  gen++; const my = gen; mode = 'shift'; document.body.classList.add('shifting'); stopAll(); music(seg.music || (BUF.shift || rawSnd.shift ? 'shift' : 'drone')); amb('static', false);
  choiceEl.classList.add('hide'); dlgEl.classList.add('hide'); tapEl.classList.add('hide'); chapEl.classList.add('hide'); ruleEl.classList.add('hide');
  // пульт, записи и кто где: тот же час заново (смерть → «Попробовать снова», «Продолжить» посреди часа) — как было в начале часа, а не к смерти (открытый вход, погашенный свет);
  // следующий час — как в конце прошлого; после перезагрузки — из сохранения (иначе вход «ОТКРЫТ», Гаса нет на седьмом, нет кнопки 13)
  const s0 = LS.get('shk0', null);
  if (th.fresh) shk = shkNew(); else if (s0 && s0.id === th.shift && s0.k) shk = Object.assign(shkNew(), s0.k); else if (!shk) shk = Object.assign(shkNew(), LS.get('shk', null));
  if (LS.get('doorLock', null) === th.shift) { shk.door = 1; LS.set('doorLock', null); }   // вход остался открытым ещё с прошлого часа — иначе повтор снова кончался «ТЫ ОТКРЫЛ ЕЙ»
  LS.set('shk0', { id: th.shift, k: shk });
  if (seg.st) Object.assign(shk.st, seg.st);   // что на камерах с начала часа (story.js: после 03:00 Гаса на седьмом нет)
  LS.set('shk', shk);
  shf = { id: th.shift, seg, my, t0: now(), lt: now(), m0: toMin(seg.from), m1: toMin(seg.to), M: toMin(seg.from), cam: seg.cam || '02', camT: now(), act: [], miss: 0, vq: [], pop: null, warn13: false, ended: false };
  shf.ev = seg.events.map(e => Object.assign({ m: toMin(e.at) }, e, e.task && { task: Object.assign({}, e.task) }, e.call && { call: Object.assign({}, e.call) }));   // задача и звонок — копии: отметки «снял трубку», «успел» оставались в сценарии, и при повторе часа звонок висел без звонка, срок задачи не проверялся
  voiceWant.clear(); voiceReady(objKeys([seg, ...Object.entries(S.shift).filter(([k]) => !/^(segs|lines)$/.test(k)).map(([, v]) => v)]));   // реплики этого часа, странностей и звонков
  if (seg.rnd) { const pool = seg.rnd.pool.slice(); for (let i = 0; i < seg.rnd.n && pool.length; i++) { const id = pool.splice(Math.random() * pool.length | 0, 1)[0]; shf.ev.push({ m: shf.m0 + 6 + Math.random() * Math.max(1, shf.m1 - shf.m0 - 14), an: id }); } }
  shf.ev.sort((a, b) => a.m - b.m);
  LS.set('save', 'sh_' + th.shift); sceneId = 'sh_' + th.shift;
  clockEl.textContent = seg.from; clockEl.classList.remove('hide'); $('hud').classList.remove('hide');
  const willTut = th.shift === 'S1' && !LS.get('tutDone', 0);   // новичку — диспетчер объясняет (подсказка-тост тогда не нужна)
  shiftUI(); if (!willTut) shiftToast(seg.hint); requestAnimationFrame(tp => shiftTick(tp, my));
  if (willTut) setTimeout(() => { if (shf && shf.my === my && gen === my && mode === 'shift' && !tut) tutorial(); }, 900);
}
function shiftProps() {   // что сейчас видно на камерах: пульт + кто где + активные странности
  const p = Object.assign({}, shk.st, { clk: shf.M, l7: shk.lights[7] ? 1 : 0, l10: shk.lights[10] ? 1 : 0 });
  if (shk.st.blackout) Object.assign(p, { hallDark: 1, stDark: 1, mOff: 1, l7: 0, l10: 0 });   // свет гаснет этаж за этажом — камеры в ИК
  for (const a of shf.act) { Object.assign(p, a.def.props || {}); if (a.def.stare) { const k = a.look || 0; p['stare' + a.def.cam] = k; if (a.def.at) { p['stareAt' + a.def.cam] = stareSpot(a.def, k); p['stareN' + a.def.cam] = stareN(k); p['stareP' + a.def.cam] = a.def.pose3; } } }   // stare07 — сколько секунд игрок уже смотрит на «помехи» (фигура проступает)
  const rt = radioTalk(); Object.assign(p, rt); shf.gusM = rt.gusR ? rt.gusR.m : 1; p.gz = shf.gz || 0;
  if (shf.id === 'S6' || shf.id === 'A1') p.noEthan = 1;   // Итан не за столом: в серверной с Фрэнком (S6) или прячется за стойкой (A1) — в будке на камерах его нет
  return p;
}
function sayV(k, o = {}) {   // реплика во время смены: голос + слова по таймингу (нет озвучки — печатается текст)
  const d = durOf(k), at = Math.max(now(), ...shf.vq.map(v => v.t0 + (v.show || v.d) + .25)), it = { k, t0: at + (o.delay || 0), d: TIM[k] ? d : Math.max(2.4, ((S.shift.lines || {})[k] || '').length / 14 + 1) };
  it.show = SUB_ONLY ? Math.max(it.d, readT(k)) : it.d;   // субтитры под английскую запись: строка стоит, сколько её читать, а следующая ждёт
  shf.vq.push(it);
  const go = () => { if (TIM[k]) for (const vo of voiceFx(k)) { const x = play(k, Object.assign({ bus: 'voice' }, vo, { when: it.t0 - now() + (vo.dt || 0) })); if (x) x.voice = true; } };
  if (TIM[k] && !BUF[k] && AC) voiceReady([k]).then(() => { if (shf && shf.vq.includes(it)) go(); }); else go();   // ещё не декодирована — догоним, когда готова
  return it;
}
const SUB_ONLY = VL !== LNG, CPS = { ja: 5, zh: 8, ko: 11 };   // язык субтитрами под чужую запись; знаков в секунду, которые успевают прочесть (как в стандартах субтитров: японский ~4–5, китайский ~9, корейский ~12, латиница ~16)
const readT = k => .9 + String((TIM[k] || {}).t || tr((S.shift.lines || {})[k] || '')).length / (CPS[LNG] || 16);
// Гас и Итан на камерах во время разговора по рации (смена S2): позы считает кадр (scenes.js cam07/cam02) по этим долям 0..1
const GUS_TAKE = 2, sgE = (t, a, b) => ease(clamp((t - a) / (b - a), 0, 1));
function radioTalk() {
  const out = {}, T = now(), g = shf.gusR, r = shf.radioR;
  for (const R of [g, r]) if (R) { let u = 0; for (const v of R.its) if (whoOf(v.k) === 'ИТАН' && aloudOf(v.k)) u = Math.max(u, sgE(T, v.t0 - .55, v.t0 - .1) * (1 - sgE(T, v.t0 + v.d + .15, v.t0 + v.d + .6))); out.ethR = Math.max(out.ethR || 0, u); }   // Итан отвечает — рация у рта
  if (r && T > Math.max(...r.its.map(v => v.t0 + v.d)) + 1) shf.radioR = null;
  if (!g) return out;
  // Гас: перестал мыть (0,9 с) → швабра к боку (0,45) → рация в руке (0,55); говорит — рация у рта, Итан отвечает — у груди; разговор кончился — рация на место, швабра перед собой, снова моет
  const t = T - g.t0, its = g.its; let last = 0; its.forEach((v, i) => { if (whoOf(v.k) === 'ГАС') last = i; });
  const nx = its[last + 1], end = (nx && whoOf(nx.k) === 'ИТАН' && aloudOf(nx.k) ? nx.t0 + nx.d : its[last].t0 + its[last].d) - g.t0;   // Гас слушает ответ Итана; мысли Итана — уже нет
  if (t > end + 3) { shf.gusR = null; return out; }
  let u = 0; for (const v of its) if (whoOf(v.k) === 'ГАС') { const a = v.t0 - g.t0; u = Math.max(u, sgE(t, a - .45, a - .05) * (1 - sgE(t, a + v.d + .1, a + v.d + .55))); }
  out.gusR = { m: Math.max(1 - sgE(t, 0, .9), sgE(t, end + 2, end + 2.9)), b: Math.min(sgE(t, .9, 1.35), 1 - sgE(t, end + 1.5, end + 1.95)), h: Math.min(sgE(t, 1.35, 1.9), 1 - sgE(t, end + .85, end + 1.45)), u };
  return out;
}
function shiftEvent(e) {
  const S2 = S.shift; if (!flagOk(e.if)) return;   // событие часа может зависеть от того, что нашёл раньше
  setFlags(e.set);
  if (e.an) { const def = S2.an[e.an]; if (!def || shf.act.some(a => a.id === e.an)) return;
    const busy = shf.act.some(a => a.def.cam === def.cam) || (def.need === 'locked' && !shk.door) || (def.need === 'nogus' && shk.st.gus) || (def.need === 'gus' && !shk.st.gus);
    if (busy) { const K = SEC_MIN / (shf.seg.spm || SEC_MIN); if (shf.M + 4 * K < shf.m1 - 6) { e.done = false; e.m = shf.M + (3 + Math.random() * 3) * K; } return; }   // камера занята или не к месту — позже
    shf.act.push({ id: e.an, def, m: shf.M });
    if (def.light) shk.lights[def.light] = 1;   // свет включился сам — на пульте этаж загорелся
    if (def.unlock) shk.door = 0;   // замок входа отщёлкнулся сам — на пульте снова «ОТКРЫТ»
    if (def.trap === 'door' && !shk.door) shiftToast(tr('Вход не заперт!'));
    if (def.sfx) play(def.sfx[0], def.sfx[1] || {}); if (def.notice) shiftToast(def.notice); }
  if (e.say) sayV(e.say);
  if (e.sfx) play(e.sfx[0], e.sfx[1] || {});   // звук события (звонит телефон Фрэнка на столе)
  if (e.radio) {   // по рации. Начинает Гас, а он на камере 07 моет пол — сперва отставляет швабру и снимает рацию с нагрудника; щелчок и голос — когда рация у рта
    const gus = !!shk.st.gus && whoOf(e.radio[0]) === 'ГАС', dl = gus ? GUS_TAKE : 0;
    play('click', { gain: .4, when: dl }); const its = e.radio.map((k, i) => sayV(k, { delay: i ? 0 : dl }));
    if (gus) shf.gusR = { t0: now(), its }; else shf.radioR = { t0: now(), its };
  }
  if (e.sms) { showSms(e.sms); buzz(60); play('buzz', { gain: .7 }); if (e.sms.reply) setTimeout(() => shiftReply(e.sms), 2600); }
  if (e.state) Object.assign(shk.st, e.state);
  if (e.c13) { shk.c13 = true; shiftUI(); }
  if (e.hint) shiftToast(e.hint);
  if (e.task) shf.task = e.task, shiftTask();
  if (e.call) shiftCall(e.call);
  if (e.hold) shiftHold(e.hold);
}
function shiftResolve(a, how) {
  shf.act = shf.act.filter(x => x !== a); play('click', { gain: .5 }); if (a.id !== 'tut13') run.ok++;
  if (a.def.ok) sayV(a.def.ok); else if (how) shiftToast(how);
  shiftTask();
}
function shiftCheck() {   // после записи или пульта: что из странностей уже улажено
  for (const a of [...shf.act]) { const f = a.def.fix || 'log';
    if (f.startsWith('light:') && !shk.lights[+f.slice(6)]) shiftResolve(a, tr('Свет выключен.'));
    else if (f === 'lift' && shk.lift) shiftResolve(a, tr('Лифт остановлен.'));
    else if (f === 'door' && shk.door) shiftResolve(a, tr('Вход заперт.')); }
  if (shf.task) shiftTask();
}
function shiftMiss(a) {
  shf.act = shf.act.filter(x => x !== a); if ((a.def.fix || 'log') === 'none') return;
  shf.miss++; run.miss++; play('heart', { gain: .9 }); setTimeout(() => play('heart', { gain: .7 }), 700); play('steps_barefoot', { gain: .35, cut: 1.6, pan: Math.random() - .5, lp: 900 });
  buzz([60, 80, 60]); shiftToast(shf.miss >= 3 ? '' : [tr('Что-то упустил…'), tr('Где-то в здании хлопнула дверь.'), tr('Шаги. Далеко. Или нет.')][Math.min(2, shf.miss - 1)]);
  if (shf.miss >= 3) shiftDie('over_come');
}
const DEATH_ALT = { over_stare: ['over_stare', 'over_stare_b'] };   // одна причина смерти — разные финалы по очереди (засмотрелся: «тсс» / ладонь на объективе)
// финал, снятый на одной камере: «ладонь на объективе» — это CAM 07, пустой седьмой. Засмотрелся на другой камере (или Гас ещё моет седьмой) — «тсс»
const ALT_OK = { over_stare_b: cam => cam === '07' && !shk.st.gus };
function shiftDie(id, cam) { if (!shf || shf.ended) return; shf.ended = true; mgEl.classList.add('hide'); shiftPop(null);
  const alt = DEATH_ALT[id] && DEATH_ALT[id].filter(x => !ALT_OK[x] || ALT_OK[x](cam)); if (alt && alt.length) { const n = +LS.get('alt_' + id, 0) || 0; LS.set('alt_' + id, n + 1); id = alt[n % alt.length]; }
  id = ((S.shift.hourDeath || {})[shf.id] || {})[id] || id;   // в серверной (S6) и под стойкой (A1) Итана нет в будке — та же смерть в другой постановке
  if (id === 'over_door') LS.set('doorLock', shf.id);   // умер у открытого входа — «снова» начнёт этот час с запертым входом
  startScene(id); }
function shiftEnd() { if (shf.ended) return; shf.ended = true; mgEl.classList.add('hide'); shiftPop(null); const n = shf.seg.next;
  if (!run.hrs.includes(shf.id)) run.hrs.push(shf.id); runSave(); LS.set('shk', shk); shf = null; startScene(n); }
function shiftTick(tp, my) {   // my — чей это цикл: час, начатый прямо из другого часа (startScene → shiftGame без кадра между), иначе крутил два цикла — время шло вдвое быстрее
  if (!shf || shf.my !== gen || mode !== 'shift' || shf.ended || my !== shf.my) return;
  const t = now(), dt = Math.min(.1, t - shf.lt); shf.lt = t;
  shf.gz = (shf.gz || 0) + dt * (shf.gusM ?? 1);   // часы швабры Гаса: пока он говорит по рации, стоят — потом моет дальше с того же места
  shf.gm = (shf.gm || 0) + dt * shSpd / (shf.seg.spm || SEC_MIN); const M = shf.M = shf.m0 + shf.gm; lastTime = minStr(M); secBase = Math.floor((M % 1) * 60); sceneT0 = t;   // spm — свой темп часа (секунд на минуту)
  clockEl.textContent = minStr(M);
  for (const e of shf.ev) if (!e.done && M >= e.m) { e.done = true; shiftEvent(e); if (shf.ended) return; }
  for (const a of [...shf.act]) { const d = a.def;
    if (shf.cam === d.cam && d.snd && t - shf.camT > .3 && t >= (a.sndT || 0)) { a.sndT = t + (d.snd[2] || 6) * (.8 + Math.random() * .4); for (let i = 0; i < (d.snd[3] || 1); i++) play(d.snd[0], Object.assign({ gain: .7 }, d.snd[1] || {}, { when: i * .3 })); shf.mic = 1; }   // камера слышит
    if (shf.cam === d.cam && !shf.pop && t - shf.camT > .4) {
      if (!a.seen) { a.seen = t; if (d.say) sayV(d.say); }
      if (d.stare) { a.look = (a.look || 0) + dt; if (a.look > 5) return shiftDie(d.death || 'over_stare', d.cam); if (a.look > 2.6 && !a.warned) { a.warned = true; play('breath_close', { gain: .6 }); shakeAmp = 8; } }
    } else a.look = 0;
    const K = SEC_MIN / (shf.seg.spm || SEC_MIN);   // окна странностей — в настоящих секундах, как при обычном темпе
    if (d.trap === 'door' && !shk.door && M > a.m + 4 * K) return shiftDie(d.die || 'over_door');   // не запер — она вошла
    if (M > a.m + (d.win || 15) * K) shiftMiss(a); if (shf.ended) return; }
  if (shf.task && shf.task.sayAt != null && M >= shf.task.sayAt) { shf.task.sayAt = null; sayV(shf.task.say); }   // «Двадцать два ноль-ноль. Вход заперт…» — к сроку, а не в ту минуту, когда запер
  if (shf.task && M > toMin(shf.task.by) && !shf.task.done) { const tk = shf.task; tk.done = true; if (!(tk.need || []).every(n => shk[n])) { shf.miss++; run.miss++; shiftToast(tr('Не успел к сроку.')); if (shf.miss >= 3) return shiftDie('over_come'); } shiftTask(); }
  if (M >= shf.m1) return shiftEnd();
  requestAnimationFrame(tp2 => shiftTick(tp2, my));
}
// --- интерфейс смены ---
function shiftToast(s) { if (!s) return; const el = $('shToast'); if (!el) return; el.textContent = tr(s); el.classList.add('on'); clearTimeout(el._t); el._t = setTimeout(() => el.classList.remove('on'), 4200); }
function shiftTask() { const el = $('shTask'); if (!el) return; const tk = shf && shf.task;
  if (!tk || (tk.need || []).every(n => shk[n])) { if (tk && !tk.ok) { tk.ok = true; if (tk.say) tk.sayAt = Math.max(shf.M, toMin(tk.by) - 3); } el.classList.add('hide'); return; }
  el.classList.remove('hide'); el.innerHTML = `<b>${tr('ЗАДАЧА')}</b> ${tr(tk.text)}`; }
function shiftUI() {
  const cams = shf.seg.cams.concat(shk.c13 ? ['13'] : []);
  mgEl.innerHTML = `<div class="sh">
    <div class="sh-task hide" id="shTask"></div><div class="sh-toast" id="shToast"></div>
    <div class="sh-cams">${cams.map(c => `<button data-c="${c}" class="${c === shf.cam ? 'on' : ''}${c === '13' ? ' c13' : ''}"><b>${camNo(c)}</b><small>${tr(CAMS[c] || '???')}</small></button>`).join('')}</div>
    <div class="sh-act"><button id="shLog">${ICON.pen || ''}<span>${tr('Записать')}</span></button><button id="shPanel"><span>${tr('Пульт')}</span></button><button id="shBook"><span>${tr('Журнал')}</span></button><button id="shHelp" class="sh-q" title="${tr('Как играть')}">?</button></div>
    <div class="tut-card hide" id="tutCard"><div class="tut-hd"><i class="tut-wave"><b></b><b></b><b></b><b></b><b></b></i>${tr('ПУЛЬТ «ЩИТ» · ДИСПЕТЧЕР ВЕРА')}</div><div class="tut-do" id="tutDo"></div><button id="tutGo" class="tut-go hide">${tr('Продолжить')}</button><button id="tutSkip">${tr('Пропустить обучение')}</button></div>
    <div class="tut-ring hide" id="tutRing"><i>▼</i></div>
    <div class="sh-pop hide" id="shPop"></div></div>`;
  mgEl.classList.remove('hide');
  mgEl.querySelectorAll('.sh-cams button').forEach(b => b.onclick = e => { e.stopPropagation(); shiftCam(b.dataset.c); });
  $('shLog').onclick = e => { e.stopPropagation(); shiftLog(); }; $('shPanel').onclick = e => { e.stopPropagation(); shiftPanel(); }; $('shBook').onclick = e => { e.stopPropagation(); shiftBook(); };
  $('shHelp').onclick = e => { e.stopPropagation(); shiftHelp(); }; $('tutSkip').onclick = e => { e.stopPropagation(); tutEnd(true); };
  if (tut) $('tutCard').classList.remove('hide');
  shiftTask();
}
function shiftCam(c) {
  if (c === '13') { if (!shf.warn13) { shf.warn13 = true; play('static', { gain: .5, cut: .4 }); shiftToast('«НЕ СМОТРИ В НЕЁ» — журнал, 18.10'); return; } return shiftDie('over_k1'); }
  if (c === shf.cam) return; shf.cam = c; shf.camT = now(); play('click', { gain: .45 }); play('static', { gain: .25, cut: .25 }); heldT = -9;
  mgEl.querySelectorAll('.sh-cams button').forEach(b => b.classList.toggle('on', b.dataset.c === c));
}
function shiftPop(html, noX) { const el = $('shPop'); if (!el) return; shf && (shf.pop = html ? 1 : null); if (!html) { el.classList.add('hide'); el.innerHTML = ''; return; }
  el.innerHTML = html + (noX ? '' : `<button class="sh-x" id="shX">${tr('Закрыть')}</button>`); el.classList.remove('hide'); if (!noX) $('shX').onclick = e => { e.stopPropagation(); shiftPop(null); }; }
function shiftLog() {
  const c = shf.cam;
  shiftPop(`<h3>${tr('Записать в журнал')}</h3><p>${camLabel(c)} · ${minStr(shf.M)}</p><div class="sh-grid">${KINDS.concat(['Всё спокойно']).map((k, i) => `<button data-i="${i}">${tr(k)}</button>`).join('')}</div>`);
  $('shPop').querySelectorAll('.sh-grid button').forEach(b => b.onclick = e => { e.stopPropagation(); const k = (KINDS.concat(['Всё спокойно']))[+b.dataset.i];
    const hit = shf.act.find(a => a.def.cam === c && (a.def.fix || 'log') === 'log' && (a.def.kinds || [a.def.kind]).includes(k)), my = shf.my;
    shk.log.push({ m: minStr(shf.M), cam: c, k, ok: !!hit }); run.rec++; play('pen', { gain: .5, cut: 1.3 });
    shiftPop(`<h3>${tr('Записываю…')}</h3><p>${camLabel(c)} · ${minStr(shf.M)} — ${tr(k)}</p>`, true);
    setTimeout(() => { if (!shf || shf.my !== my || shf.ended) return; shiftPop(null);
      if (hit && shf.act.includes(hit)) shiftResolve(hit, tr('Записано.')); else shiftToast(k === 'Всё спокойно' ? tr('Записано: всё спокойно.') : tr('Записано. Хотя, может, показалось.'));
      shiftCheck(); }, 1400); });
}
function shiftPanel() {
  if (tut) tut.seen.panel = 1;
  const fl = [12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, -2];
  const draw = () => shiftPop(`<h3>${tr('Пульт')}</h3><div class="sh-row"><button id="pDoor" class="${shk.door ? 'on' : ''}">${tr('Вход')}: <b>${tr(shk.door ? 'ЗАПЕРТ' : 'ОТКРЫТ')}</b></button>
      <button id="pLift" class="${shk.lift ? 'on' : ''}">${tr('Лифт')}: <b>${tr(shk.lift ? 'СТОП' : 'РАБОТАЕТ')}</b></button></div>
    <p>${tr('Свет на этажах')}</p><div class="sh-lights">${fl.map(f => `<button data-f="${f}" class="${shk.lights[f] ? 'lit' : ''}">${f === -2 ? '−2' : f}</button>`).join('')}</div>`);
  const wire = () => { $('pDoor').onclick = e => { e.stopPropagation(); shk.door ^= 1; play('click'); if (shk.door) play('bolt', { gain: .4, cut: .8 });
      const trap = !shk.door && shf.act.find(a => a.def.trap === 'door'); if (trap) return shiftDie(trap.def.die || 'over_door');   // открыл ей
      draw(); wire(); shiftCheck(); };
    $('pLift').onclick = e => { e.stopPropagation(); shk.lift ^= 1; play('click'); draw(); wire(); shiftCheck(); };
    $('shPop').querySelectorAll('.sh-lights button').forEach(b => b.onclick = e => { e.stopPropagation(); const f = +b.dataset.f; shk.lights[f] = shk.lights[f] ? 0 : 1; play('click', { gain: .6 }); draw(); wire(); shiftCheck(); }); };
  draw(); wire();
}
function shiftBook() {
  if (tut) tut.seen.book = 1;
  const fr = flags.jread ? S.shift.frank : S.shift.frankPre || S.shift.frank;   // личные записи Фрэнка — в перевязанном журнале: до 02:50 не прочитаны
  const tabs = [['Памятка', (S.shift.memo || []).map(s => `<li>${tr(s)}</li>`).join('')], ['Фрэнк', (fr || []).map(s => `<li>${tr(s)}</li>`).join('')],
    ['Моя смена', shk.log.length ? shk.log.map(r => `<li>${r.m} · ${camLabel(r.cam)} — ${tr(r.k)}</li>`).join('') : `<li>${tr('Пока ни одной записи.')}</li>`]];
  const show = i => { shiftPop(`<h3>${tr('Журнал')}</h3><div class="sh-tabs">${tabs.map((x, j) => `<button data-j="${j}" class="${j === i ? 'on' : ''}">${tr(x[0])}</button>`).join('')}</div><ul class="sh-page">${tabs[i][1]}</ul>`);
    $('shPop').querySelectorAll('.sh-tabs button').forEach(b => b.onclick = e => { e.stopPropagation(); show(+b.dataset.j); }); if (i === 1) shk.read = true; };
  show(0);
}
function shiftReply(m) {   // ответ на СМС: два варианта
  shiftPop(`<h3>${tr(m.from || 'Лили')}</h3><p>${tr(m.text)}</p><div class="sh-grid">${m.reply.map((r, i) => `<button data-i="${i}">${tr(r)}</button>`).join('')}</div>`);
  $('shPop').querySelectorAll('.sh-grid button').forEach(b => b.onclick = e => { e.stopPropagation(); shiftPop(null); play('click'); if (m.back) setTimeout(() => { if (shf) { showSms({ from: m.from, text: m.back }); play('buzz', { gain: .6 }); } }, 4000); });
}
// --- обучение: диспетчер охранного пульта «Щит» Вера по рации показывает, что где. Время смены стоит; можно пропустить; «?» — повторить ---
let tut = null;
const TUT_DO = { t02: 'Нажми камеру 03 — лифты', t03: 'Смотри на табло лифта: «13»', t04: 'Нажми «Записать» → «Лифт»', t06: 'MIC слева вверху: столбики прыгают — звук', t07: 'Открой «Пульт»', t08: 'Открой «Журнал»' };
function tutWait(cond) { const me = tut; return new Promise(res => { let n = 0; const f = () => { if (tut !== me || !me || shf !== me.shf || shf.ended || mode !== 'shift') return res(false); if (cond()) return res(true); if (++n % 15 === 0) tutMark(me.target); requestAnimationFrame(f); }; f(); }); }
function tutMark(target) {   // кольцо со стрелкой вокруг кнопки (селектор) или прямоугольника холста [x, y, w, h] в единицах надписей HUD
  const ring = $('tutRing'), box = mgEl.querySelector('.sh'); if (!ring || !box) return; if (tut) tut.target = target;
  if (!target) { ring.classList.add('hide'); return; }
  const b = box.getBoundingClientRect(); let r;
  if (Array.isArray(target)) { const c = cv.getBoundingClientRect(), k = c.width / W; r = { left: c.left + target[0] * US * k, top: c.top + target[1] * US * k, width: target[2] * US * k, height: target[3] * US * k }; }
  else { const el = mgEl.querySelector(target); if (!el) { ring.classList.add('hide'); return; } r = el.getBoundingClientRect(); }
  const p = 6; Object.assign(ring.style, { left: (r.left - b.left - p) + 'px', top: (r.top - b.top - p) + 'px', width: (r.width + 2 * p) + 'px', height: (r.height + 2 * p) + 'px' }); ring.classList.remove('hide');
}
function tutGo(target, it) {   // как в диалогах: Вера договорила — ждём «Продолжить» (клик, Пробел/Enter, A на пульте); «Авто» — сам, когда текст прочитан
  const b = $('tutGo'); if (!b) return Promise.resolve(true);
  let go = false; const tA = performance.now() / 1000 + Math.max(1.2, readT(it.k) - it.d);
  b.classList.remove('hide'); b.onclick = e => { e.stopPropagation(); go = true; play('click'); }; if (!target) tutMark('#tutGo');
  return tutWait(() => go || (auto && performance.now() / 1000 > tA && !paused)).then(r => { b.classList.add('hide'); b.onclick = null; return r; });
}
async function tutorial() {
  if (!shf || mode !== 'shift' || !$('tutCard')) return; const me = tut = { target: null, seen: {}, shf }; shSpd = 0; $('tutCard').classList.remove('hide'); shiftPop(null);
  const card = (cls, on) => { const c = $('tutCard'); if (c) c.classList.toggle(cls, on); };
  const step = async (k, target, cond) => { if (tut !== me || !$('tutDo')) return false; tutMark(target); $('tutDo').textContent = tr(TUT_DO[k] || ''); card('talk', true);
    const it = sayV(k), rEnd = performance.now() / 1000 + (it.t0 - now()) + it.d + .3;   // голос идёт в реальном времени, даже если кадры медленные
    it.hold = true; const done = r => { it.hold = false; return r; };   // субтитр Веры не убегает: стоит, пока игрок не сделал, что она просит, или не нажал «Продолжить»
    if (!(await tutWait(() => performance.now() / 1000 > rEnd && !paused))) return done(false); card('talk', false);
    return done(await (cond ? tutWait(cond) : tutGo(target, it))); };
  const ok = await (async () => {
    if (!await step('t01')) return false;
    if (!await step('t02', 'button[data-c="03"]', () => shf.cam === '03')) return false;
    shf.act.push({ id: 'tut13', def: { cam: '03', kind: 'Лифт', props: { lift13: 1 }, win: 9999 }, m: shf.M });   // учебная странность: табло показывает 13-й этаж
    if (!await step('t03')) return false;
    if (!await step('t04', '#shLog', () => !shf.act.some(a => a.id === 'tut13'))) return false;
    if (!await step('t05')) return false;
    if (!await step('t06', [76, 340, 290, 44])) return false;
    if (!await step('t07', '#shPanel', () => me.seen.panel)) return false;   // открыл пульт (хоть раньше, чем она договорила)
    tutMark(null); if (!await tutWait(() => !shf.pop)) return false;   // посмотрел пульт и закрыл
    if (!await step('t08', '#shBook', () => me.seen.book)) return false;
    tutMark(null); if (!await tutWait(() => !shf.pop)) return false;
    return step('t09');
  })();
  if (tut === me) tutEnd(ok);
}
function tutEnd(done) {
  if (!tut) return; tut = null; shSpd = 1; if (done) LS.set('tutDone', 1); tutMark(null);   // ушёл со смены посреди обучения — в следующий раз начнётся снова
  const c = $('tutCard'); if (c) { c.classList.add('hide'); c.classList.remove('talk'); }
  if (shf) { shf.act = shf.act.filter(a => a.id !== 'tut13'); shf.vq = shf.vq.filter(v => !/^t0\d$/.test(v.k)); }
  if (shf && mode === 'shift') for (const o of [...playing]) if (o.voice) { try { o.s.stop(); } catch {} playing.delete(o); }   // пропустил — Вера замолкает
  if (done && shf && mode === 'shift') shiftToast(tr('Смена началась. Удачи.'));
}
function shiftHelp() {   // шпаргалка: как играть; можно пройти обучение снова
  shiftPop(`<h3>${tr('Как играть')}</h3><ul class="sh-page">${['Камеры — кнопки внизу. Переключай и запоминай, как там обычно.', 'Заметил, что не так, — «Записать» и выбери, что это: свет, дверь, лифт, помехи, кто-то, звук, другое.',
    'MIC слева вверху: прыгают столбики — на этой камере звук.', '«Пульт»: свет на этажах, вход, лифт. Лишний свет — выключи. К 22:00 вход запри, лифт останови.',
    'Три пропущенные странности за час — оно придёт к будке.', 'Помехи на камере — не засматривайся: записал и переключил.', 'Не отзывайся, если зовут по имени. Не открывай дверь.'].map(x => `<li>${tr(x)}</li>`).join('')}</ul>
    <button class="sh-x" id="tutAgain">${tr('Пройти обучение снова')}</button>`);
  $('tutAgain').onclick = e => { e.stopPropagation(); shiftPop(null); if (!tut) tutorial(); };
}
function shiftHold(h) {   // держать кнопку, пока голос не стихнет: голос матери зовёт Фрэнка (S6) или «Лили» у будки (A1, «НЕ ДЫШИ»); отпустил — смерть h.die
  const my = shf.my, it = sayV(h.say, { delay: .4 }), need = h.dur || 7, B = tr(h.btn || 'ДЕРЖАТЬ'), BON = tr(h.btnOn || 'ДЕРЖУ'); let held = 0, down = false, last = now(), lost = 0;
  shiftPop(`<h3>${tr(h.title || 'ДЕРЖИ ФРЭНКА')}</h3><p>${tr(h.text || 'Фрэнк поднимается на голос. Не отпускай, пока голос не стихнет.')}</p><div class="sh-hold"><i id="hBar"></i></div><button id="hBtn" class="sh-holdb">${B}</button>`, true);
  play('heart', { gain: .9 }); if (h.sfx) play(h.sfx[0], h.sfx[1] || {}); const btn = $('hBtn');
  const on = e => { e.preventDefault(); e.stopPropagation(); try { btn.setPointerCapture(e.pointerId); } catch {} down = true; btn.classList.add('on'); btn.textContent = BON; }, off = e => { e && e.preventDefault(); down = false; btn.classList.remove('on'); btn.textContent = B; };
  btn.addEventListener('pointerdown', on); btn.addEventListener('pointerup', off); btn.addEventListener('pointerleave', off); btn.addEventListener('pointercancel', off);
  const step = () => { if (!shf || shf.my !== my || shf.ended) return; const t = now(), dt = Math.min(.1, t - last); last = t;
    if (t > it.t0) { if (down) held += dt; else if (t > it.t0 + 1.2) lost += dt; }   // секунда с небольшим на то, чтобы схватить
    const bar = $('hBar'); if (bar) bar.style.width = Math.min(100, held / need * 100) + '%';
    if (lost > .9) { shiftPop(null); return shiftDie(h.die || 'over_k8'); }
    if (held >= need) { shiftPop(null); play('click'); shiftToast(tr(h.done || 'Голос стих. Фрэнк сел на пол и закрыл лицо руками.')); return; }
    requestAnimationFrame(step); };
  requestAnimationFrame(step);
}
function shiftCall(c) {   // внутренний телефон будки: снять или нет; сняли — голос по имени; ответить — смерть
  let rings = 0, btn = null; const my = shf.my;
  const ring = () => { if (!shf || shf.my !== my || shf.ended || c.taken || rings >= (c.rings || 6)) { if (!c.taken && shf && shf.my === my && btn && btn.isConnected) shiftPop(null); return; }   // не дождались — закрываем только сам звонок, не пульт поверх него
    if (paused) return void setTimeout(ring, 400);   // пауза (☰ или свернул вкладку): звонок ждёт, а не «дозванивается» молча и не пропадает
    rings++;
    if (c.radio) play('static', { gain: .5, cut: .9 }); else play('ring', { gain: .8 }); buzz(80); setTimeout(ring, 2200); };
  shiftPop(`<h3>${tr(c.title || 'Звонит внутренний телефон')}</h3><p>${tr(c.sub || 'В будке. Номер не определён.')}</p><div class="sh-grid"><button id="cTake">${tr(c.take || 'Снять трубку')}</button><button id="cNo">${tr(c.no || 'Не брать')}</button></div>`);
  btn = $('cTake'); ring();
  $('cNo').onclick = e => { e.stopPropagation(); c.taken = 'no'; shiftPop(null); play('click'); if (c.noSay) sayV(c.noSay); };   // сбросил — что он подумал (noSay)
  $('cTake').onclick = e => { e.stopPropagation(); c.taken = 'yes'; shiftPop(null); play('click'); if (c.takeDie) return shiftDie(c.die);   // ответить на такой звонок — уже ответить ему
    const it = sayV(c.say);
    setTimeout(() => { if (!shf || shf.my !== my || shf.ended) return;
      shiftPop(`<h3>${tr(c.radio ? 'В эфире' : 'В трубке')}</h3><p>${tr(c.ask)}</p><div class="sh-grid"><button id="cYes">${tr(c.yes)}</button><button id="cHang">${tr(c.hang)}</button></div>`);
      $('cYes').onclick = e2 => { e2.stopPropagation(); shiftDie(c.die); };
      $('cHang').onclick = e2 => { e2.stopPropagation(); shiftPop(null); play('click'); if (c.after) sayV(c.after); }; }, Math.max(600, (it.t0 + it.d - now()) * 1000)); };
}
// найденные смерти: в сохранении — русские названия (старые сохранения — на языке, на котором играли); считаем по номеру в S.overs
const oversGot = () => { const s = new Set(); for (const k of LS.get('overs', [])) { let i = OVERS0.indexOf(k); if (i < 0) i = S.overs.indexOf(k); if (i >= 0) s.add(i); } return s; };
function gallery() {
  const got = LS.get('endings', []).filter(k => S.endings[k]), ov = oversGot();
  return `<div class="gal">${Object.entries(S.endings).map(([k, n]) => `<div class="${got.includes(k) ? 'got' : ''}">${got.includes(k) ? n : '???'}</div>`).join('')}</div>
    <small>${trf('Концовки: {a} из {b} · Смертей найдено: {c} из {d}', { a: got.length, b: Object.keys(S.endings).length, c: ov.size, d: S.overs.length })}</small>`;
}
/* ---------- спонсоры игры (донаты звёздами Telegram через бота) ---------- */
const API = /railway\.app$|^localhost$/.test(location.hostname) ? '' : 'https://office-dreams-production.up.railway.app';
let SP = { donate: 'https://t.me/officedream1_bot?start=games', list: [] };
if (!DESK) get(API + '/api/games/sponsors', 'json').then(j => { if (j && j.ok) SP = Object.assign(SP, j, { donate: j.donate || SP.donate }); }).catch(() => {});
function sponsorsHtml() {
  const names = SP.list.slice(0, 30).map(r => `<span>${escH(r.name)}</span>`).join('');
  return `<div class="spons"><b>${tr('СПОНСОРЫ ИГРЫ')}</b>${names || `<i>${tr('Стань первым — твоё имя будет здесь')}</i>`}</div>`;
}
const supportBtn = (cls = 'btn ghost sup') => `<a class="${cls}" href="${escH(SP.donate)}" target="_blank" rel="noopener">${ICON.star}<span>${tr('Поддержать игру')}</span></a>`;
// бесплатная часть (демо для itch.io): сборка кладёт window.NS_DEMO = { price, url }, сцены после выбора у стекла в ней вырезаны (tools/itch/demo.js)
const DEMO = window.NS_DEMO || null;
function buyUrl() {
  if (DEMO && DEMO.url) return DEMO.url;
  const m = /^https:\/\/[a-z0-9-]+\.itch\.io/.exec(document.referrer || '');   // на itch игра в iframe: referrer — страница автора
  return m ? m[0] + '/hush-till-six' : 'https://itch.io/search?q=hush+till+six';   // страница игры: там «Buy Now»
}
const buyBtn = (id, cls) => `<a class="${cls}" id="${id}" href="${escH(buyUrl())}" target="_blank" rel="noopener">${ICON.star}<span>${trf('Полная версия — {p}', { p: (DEMO && DEMO.price) || '$3' })}</span></a>`;
// титульный экран: живой кадр (Катя стучит в стекло под дождём), медленный наезд, логотип мигает, как больная лампа
const TITLE_SHOT = 'door_katya';
let gateDone = false, gateT = -9, titleT0 = 0, titleFirst = true, logoURL = '';
function logoMask() {   // эрозия букв: редкие выщербины и туман снизу (маска рисуется кодом один раз)
  if (logoURL) return logoURL;
  const c = document.createElement('canvas'), w = 160, h = 128; c.width = w; c.height = h; const x = c.getContext('2d'), d = x.createImageData(w, h);
  let s = 7; const r = () => ((s = (s * 16807) % 2147483647) / 2147483647);
  for (let y = 0; y < h; y++) for (let i = 0; i < w; i++) { const k = (y * w + i) * 4, fog = y / h > .8 ? 1 - (y / h - .8) / .2 * .6 : 1; d.data[k] = d.data[k + 1] = d.data[k + 2] = 0; d.data[k + 3] = 255 * fog * (r() < .035 ? .25 + r() * .4 : 1); }
  x.putImageData(d, 0, 0); x.globalCompositeOperation = 'destination-out';
  for (let n = 0; n < 26; n++) { x.fillStyle = `rgba(0,0,0,${.35 + r() * .5})`; x.beginPath(); x.arc(r() * w, r() * h * .95, .6 + r() * 1.8, 0, 7); x.fill(); }
  for (let n = 0; n < 5; n++) { x.fillStyle = 'rgba(0,0,0,.4)'; x.fillRect(0, r() * h, w, .7); }   // царапины, как на старой плёнке
  return (logoURL = c.toDataURL());
}
// «Кино» на этой видеокарте рисовало чёрным — игра сама включила «Ретро» (scenes.js, gfxCheck): сказать об этом один раз
function gfxNote() { const a = LS.get('gfxAuto', 0); if (a !== 1 && a !== 4) return; LS.set('gfxAuto', 3);
  const n = document.createElement('div'); n.className = 'gfx-note'; n.textContent = tr(a === 4 ? 'На этом телефоне «Кино» шло слишком медленно — включена «Ретро». Вернуть: Настройки → Графика.' : 'Графика «Кино» на этом устройстве рисовала слишком темно — включена «Ретро». Вернуть: Настройки → Графика.');
  ($('stage') || document.body).appendChild(n); setTimeout(() => n.remove(), 9000); }
function menu() {
  gen++; mode = 'menu'; overBg = null; document.body.classList.remove('shifting'); gfxNote(); menuT0 = now(); titleT0 = now(); stopAll(false); music(null); for (const k in ambient) amb(k, false); if (hushOn) hush(false); $('hud').classList.add('hide'); hideSms();
  choiceEl.classList.add('hide'); mgEl.classList.add('hide'); dlgEl.classList.add('hide'); tapEl.classList.add('hide'); chapEl.classList.add('hide'); clockEl.classList.add('hide'); noteEl.classList.add('hide'); ruleEl.classList.add('hide'); waiting = false; beat = null; act = null; actEl.innerHTML = ''; actEl.classList.add('hide');
  const sv = LS.get('save', null), save = sv && S.scenes[sv] ? sv : null;   // сохранение из старой версии сценария не ломает меню
  uiEl.innerHTML = `<div class="title ${gateDone ? 'open' : 'gate'}${titleFirst ? ' first' : ''}">
    <div class="logo" id="logo"><b>HUSH</b><b>TILL SIX</b></div>
    <div class="tagl">${tr('Не отзывайся · Не открывай · Дождись шести')}</div>
    <div class="tts">${FINE ? 'CLICK TO START' : 'TAP TO START'}</div>
    <nav class="mlist">
      ${save ? `<button id="mCont">${tr('Продолжить')}</button>` : ''}
      <button id="mNew">${tr(save ? 'Начать заново' : 'Начать смену')}</button>
      <button id="mGal">${tr('Концовки')}</button>
      <button id="mSet">${tr('Настройки')}</button>
      ${DESK ? `<button id="mQuit">${tr('Выйти из игры')}</button>` : DEMO ? buyBtn('mBuy', 'msup') : supportBtn('msup')}
    </nav>
    <div class="hp">${ICON.phones}<span>${tr('Лучше в наушниках · в игре есть вспышки света')}</span></div><div class="hp"><b style="color:#e33;margin-right:6px">18+</b><span>${tr('Жестокость, кровь и сцены насилия')}</span></div></div>`;
  const lg = $('logo'), m = `url(${logoMask()})`; lg.style.webkitMaskImage = lg.style.maskImage = m; lg.style.webkitMaskSize = lg.style.maskSize = '100% 100%';
  const T0 = uiEl.querySelector('.title'), ok = () => gateDone && now() - gateT > .45;   // тот же тап, что открыл меню, не жмёт кнопку под пальцем
  const btn = (id, fn) => { const b = $(id); if (b) b.onclick = e => { e.stopPropagation(); if (!ok()) return; uiSnd(); fn(); }; };
  btn('mNew', () => begin('intro')); btn('mCont', () => begin(save)); btn('mSet', settings); btn('mQuit', () => DESK.quit());
  btn('mGal', () => { subScreen(`<h1>${tr('КОНЦОВКИ')}</h1>${gallery()}`); });
  const sup = T0.querySelector('.msup'); if (sup) sup.onclick = e => { if (!ok()) e.preventDefault(); };
  if (!gateDone) { T0.onpointerdown = e => { if (e.target.closest('button, a')) return; openGate(); }; if (navigator.webdriver) setTimeout(openGate, 1200); }   // автотесты не умеют «тапнуть, чтобы начать»
  else titleAudio();
  // мигание логотипа (больная лампа) — со звуком, если звук уже разрешён
  setTimeout(() => { if (mode === 'menu' && AC && AC.state === 'running') { play('fluor_flicker', { off: 1.45, cut: .3, gain: .16 }); tubeBuzz(.06); } }, titleFirst ? 2380 : 2380);
  titleFirst = false;
  if (window.NS_QA) NS_QA.menu();   // режим проверки (qa.js): «Сцены» и «Отправить ошибки» в меню
}
function openGate() {
  if (gateDone) return; gateDone = true; gateT = now(); unlockAudio(); initAudio();
  const T0 = uiEl.querySelector('.title'); if (T0) { T0.classList.remove('gate'); T0.classList.add('open'); T0.onpointerdown = null; }
  uiSnd('open'); titleAudio();
}
function titleAudio() { bedSet('lobby', 'glass', .9); ambNext = now() + 4; }
function subScreen(html) {   // концовки поверх титульного кадра
  uiEl.innerHTML = `<div class="screen sub"><button class="sx" id="mX" aria-label="${tr('Назад')}">${ICON.x}</button>${html}<button class="btn ghost" id="mBack">${tr('Назад')}</button></div>`;
  $('mBack').onclick = $('mX').onclick = () => { uiSnd('back'); menu(); };
}
/* ---------- пауза (☰ в игре): игра и звук замирают; продолжить / настройки / главное меню ---------- */
const pzEl = $('pz');
function setPause(on) { userPause = on; paused = document.hidden || on; if (AC) (paused ? AC.suspend() : AC.resume()).catch(() => {}); }
const inGame = () => ['play', 'choice', 'mg', 'cams', 'shift', 'explore', 'talk'].includes(mode);
function pauseMenu() {
  if (!inGame()) return;
  setPause(true);
  pzEl.innerHTML = `<div class="screen pz"><button class="sx" id="pzX" aria-label="${tr('Продолжить')}">${ICON.x}</button><h1>${tr('ПАУЗА')}</h1>
    <button class="btn" id="pzGo">${tr('Продолжить')}</button><button class="btn ghost" id="pzSet">${tr('Настройки')}</button><button class="btn ghost" id="pzMenu">${tr('Главное меню')}</button></div>`;
  pzEl.classList.remove('hide');
  $('pzGo').onclick = $('pzX').onclick = e => { e.stopPropagation(); unpause(); };
  $('pzSet').onclick = e => { e.stopPropagation(); uiSnd(); settings('pause'); };
  $('pzMenu').onclick = e => { e.stopPropagation(); unpause(true); menu(); };
}
function unpause(quiet) { pzEl.classList.add('hide'); pzEl.innerHTML = ''; setPause(false); if (!quiet) uiSnd('back'); }
// язык и графика собираются при загрузке страницы: перезагружаем, но игра продолжается с того же места (сцена и кадр или час смены и минута)
// слабый телефон: «Кино» не тянет даже на 55 % — движок (scenes.js, perfTick) просит перейти на «Ретро», игра продолжится с того же места
window.nsGfxFallback = reason => { LS.set('gfx', 'retro'); LS.set('gfxAuto', reason); reloadKeep(inGame() ? 'pause' : 'menu'); };
function reloadKeep(from) {
  const st = { at: Date.now(), from, flags: Object.assign({}, flags), falseAlarm, lastTime, shk };
  if (from === 'pause') {
    if (mode === 'shift' && shf) Object.assign(st, { scene: 'sh_' + shf.id, gm: shf.gm || 0, cam: shf.cam, miss: shf.miss });
    else if (sceneId && S.scenes[sceneId] && !sceneId.startsWith('over_')) Object.assign(st, { scene: sceneId, bi: mode === 'play' ? Math.max(0, bi) : true });
  }
  try { sessionStorage.setItem('ns1_resume', JSON.stringify(st)); } catch {}
  uiSnd(); setTimeout(() => location.reload(), 120);
}
let RESUME = null;
try { RESUME = JSON.parse(sessionStorage.getItem('ns1_resume') || 'null'); sessionStorage.removeItem('ns1_resume'); if (RESUME && Date.now() - RESUME.at > 300000) RESUME = null; } catch { RESUME = null; }
function resumeAfterReload() {   // после перезагрузки (сменили язык/графику): тап — и игра там же, где была (тап нужен, чтобы браузер разрешил звук)
  const st = RESUME; RESUME = null;
  if (!st.scene) { menu(); if (st.from === 'menu') settings('menu'); return; }
  mode = 'load';
  uiEl.innerHTML = `<div class="screen boot"><div class="card">${ICON.play}<span>${tr('Нажми, чтобы продолжить')}</span></div></div>`;
  uiEl.onclick = () => { uiEl.onclick = null; unlockAudio(); goFull(); uiEl.innerHTML = '';
    for (const k in flags) delete flags[k]; Object.assign(flags, st.flags || {}); falseAlarm = !!st.falseAlarm; if (st.lastTime) lastTime = st.lastTime; shk = st.shk || null;
    Promise.race([initAudio(), new Promise(r => setTimeout(r, 6000))]).then(() => {
      startScene(st.scene, st.bi);
      if (st.gm != null && shf) { if (st.shk) { shk = Object.assign(shkNew(), st.shk); shiftUI(); }   // середина часа: пульт и кто где — как были в эту минуту (час начался с начального снимка, а события до неё уже не повторятся)
        shf.gm = st.gm; const M = shf.m0 + st.gm; for (const e of shf.ev) if (e.m < M) e.done = true; shf.miss = st.miss || 0; if (st.cam) shiftCam(st.cam); }
    }); };
}
const VHS_LV = [0, .32, .7], VHS_N = ['ВЫКЛ', 'СЛАБО', 'ОБЫЧНО'];   // переводятся в row()
const vhsDef = () => (window.World && World.cine ? 1 : 2);   // плёнка по умолчанию: в «Кино» слабая (чистая картинка), в «Ретро» обычная
function fitHd(host) {   // заголовок экрана настроек — одно длинное слово («НАЛАШТУВАННЯ»): на вертикальном экране уходил под крестик — уменьшаем, пока не влезет
  const h = host.querySelector('.sets-hd h1'), x = host.querySelector('.sets-hd .sx'); if (!h || !x) return;
  for (let i = 0; i < 10 && h.getBoundingClientRect().right > x.getBoundingClientRect().left - 4; i++) h.style.fontSize = (parseFloat(getComputedStyle(h).fontSize) * .92).toFixed(1) + 'px';
}
// видеокарта (world.js, GPU) — мелко внизу настроек: по ней графика выбрана при первом запуске; пригодится, если игрок пришлёт снимок экрана с ошибкой
const gpuName = () => { const g = window.__W3core && window.__W3core.GPU; return FINE && g && g.name ? '<br>GPU: ' + g.name.replace(/[<>&"]/g, '') : ''; };
function settings(from = 'menu') {   // from: 'menu' — из главного меню, 'pause' — из паузы в игре (настройки поверх игры, без выхода)
  const resNow = () => +LS.get('res', 0) || 0, resList = () => [0, .5, .75, 1].concat(window.World && World.ultra ? [1.5, 2] : []);   // «Разрешение»: авто или доля; 150–200 % — суперсэмплинг «Ультра»
  const gfxNow = () => { const W = window.World || {}; return W.ultra ? 'ultra' : W.cine ? 'cine' : 'retro'; };
  const row = (id, name, val) => `<button class="set" id="${id}"><span>${tr(name)}</span><b>${tr(val)}</b></button>`;
  const vh = LS.get('vhs', vhsDef());
  const host = from === 'pause' ? pzEl : uiEl;
  host.innerHTML = `<div class="screen sub sets-scr"><div class="sets-hd"><h1>${tr('НАСТРОЙКИ')}</h1><button class="sx" id="sX" aria-label="${tr('Назад')}">${ICON.x}</button></div><div class="sets">
    ${row('sLang', 'Язык', (window.NS_LANG_NAMES || {})[LNG] || (EN ? 'ENGLISH' : 'РУССКИЙ'))}
    ${row('sLand', 'Экран', LAND ? 'ГОРИЗОНТАЛЬНО' : 'ВЕРТИКАЛЬНО')}
    <button class="set" id="sGfx"><span>${tr('Графика')}</span><b class="gfx">${['retro', 'cine'].concat(FINE ? ['ultra'] : []).map(g => `<i data-g="${g}" class="${g === gfxNow() ? 'on' : ''}">${tr({ retro: 'РЕТРО', cine: 'КИНО', ultra: 'УЛЬТРА' }[g])}</i>`).join('')}</b></button>
    ${row('sVoice', 'Озвучка', voiceOn() ? 'ВКЛ' : 'ВЫКЛ')}
    <div class="vols">${[['all', 'Громкость'], ['mus', 'Музыка'], ['voice', 'Голоса'], ['sfx', 'Звуки'], ['amb', 'Дождь и фон']].map(([k, n]) => `<div class="vol ${k}"><span>${tr(n)}</span><input type="range" min="0" max="100" step="5" value="${Math.round(VOL[k] * 100)}" data-k="${k}" aria-label="${tr(n)}"><b>${Math.round(VOL[k] * 100)}</b>${k === 'all' ? `<button class="mute${LS.get('mute', false) ? ' muted' : ''}" id="sMute" aria-label="${tr('Звук')}">${ICON.snd}</button>` : ''}</div>`).join('')}</div>
    ${window.World && World.cine ? `<button class="set wide" id="sRes"><span>${tr('Разрешение')}</span><b class="gfx">${resList().map(v => `<i data-r="${v}" class="${v === resNow() ? 'on' : ''}">${v ? Math.round(v * 100) + '%' : tr('АВТО')}</i>`).join('')}</b></button>` : ''}
    ${row('sVhs', 'Плёнка VHS', VHS_N[vh] || VHS_N[2])}
    ${row('sCalm', 'Меньше вспышек', calm() ? 'ВКЛ' : 'ВЫКЛ')}
    ${row('sAuto', 'Реплики сами', auto ? 'ВКЛ' : 'ВЫКЛ')}
    ${row('sLook', FINE ? 'Оглядываться мышью' : 'Оглядываться наклоном', lookOn() ? 'ВКЛ' : 'ВЫКЛ')}
    ${DESK && DESK.isFull ? row('sFull', 'Во весь экран', DESK.isFull() ? 'ВКЛ' : 'ВЫКЛ') + row('sBg', 'Звук в фоне', DESK.bgSound() ? 'ВКЛ' : 'ВЫКЛ') : ''}</div>
    <small>${tr('«Кино» — тени, блики, объёмный свет (для ПК). «Ретро» — плёнка и пиксели, легче для телефона. Без озвучки остаются субтитры.')}${FINE ? '<br>' + tr('Клавиши: F — весь экран, O — повернуть экран, V — озвучка, M — звук.') : ''}${gpuName()}</small><button class="btn ghost" id="mBack">${tr('Назад')}</button></div>`;
  const back = () => { uiSnd('back'); from === 'pause' ? pauseMenu() : menu(); };
  $('mBack').onclick = $('sX').onclick = back; fitHd(host);
  const re = () => { uiSnd(); settings(from); };
  $('sMute').onclick = () => { setMute(!LS.get('mute', false)); uiSnd(); $('sMute').classList.toggle('muted', LS.get('mute', false)); };
  host.querySelectorAll('.vol input').forEach(r => { r.oninput = () => { VOL[r.dataset.k] = r.value / 100; r.nextElementSibling.textContent = r.value; LS.set('vol', VOL); applyVol(); };
    r.onchange = () => probe(r.dataset.k); });   // проба громкости
  $('sVoice').onclick = () => { setVoice(!voiceOn()); re(); };
  $('sLang').onclick = () => { uiSnd(); langPick(from); };
  $('sLook').onclick = () => { LS.set('look', !lookOn()); re(); };
  $('sLand').onclick = () => { LS.set('land3', !LAND); applyLand(!LAND); re(); };
  if ($('sRes')) $('sRes').onclick = e => { const r = e && e.target && e.target.dataset ? e.target.dataset.r : null, L = resList(), cur = resNow(), nx = r != null ? +r : L[(L.indexOf(cur) + 1) % L.length]; if (nx === cur) return; LS.set('res', nx); reloadKeep(from); };
  $('sGfx').onclick = e => { const W = window.World || {}, pick = e && e.target && e.target.dataset ? e.target.dataset.g : null; if (pick && pick === gfxNow()) return;   // выбор виден сразу: тап по слову — этот вид; Enter/кнопка пульта — следующий по кругу
    LS.set('gfx', pick || (W.ultra ? 'retro' : W.cine ? (FINE ? 'ultra' : 'retro') : 'cine')); LS.set('gfxAuto', 2); reloadKeep(from); };   // ПК: Кино → Ультра → Ретро; телефон: Кино ⇄ Ретро   // графика собирается при загрузке; выбрал сам — автопроверка больше не вмешивается
  $('sVhs').onclick = () => { const v = (LS.get('vhs', vhsDef()) + 2) % 3; LS.set('vhs', v); if (window.World) World.vhs = VHS_LV[v]; re(); };
  $('sCalm').onclick = () => { LS.set('calm', !calm()); re(); };
  $('sAuto').onclick = () => { auto = !auto; LS.set('auto', auto); $('bAuto').classList.toggle('on', auto); re(); };
  if ($('sFull')) { $('sFull').onclick = () => { DESK.full(!DESK.isFull()); re(); }; $('sBg').onclick = () => { DESK.setBgSound(!DESK.bgSound()); re(); }; }   // ПК-версия: окно/весь экран; звук, когда окно в фоне (выкл — тишина и пауза)
}
function langPick(from) {   // выбор языка списком; выбранный — с перезагрузкой (надписи в 3D-мире рисуются при загрузке), игра продолжится с того же места
  const host = from === 'pause' ? pzEl : uiEl, N = window.NS_LANG_NAMES || {};
  host.innerHTML = `<div class="screen sub sets-scr"><div class="sets-hd"><h1>${tr('Язык').toLocaleUpperCase(LNG)}</h1><button class="sx" id="sX" aria-label="${tr('Назад')}">${ICON.x}</button></div><div class="sets langs">
    ${(window.NS_LANGS || ['ru', 'en']).map(l => `<button class="set" data-l="${l}"><span>${N[l] || l.toUpperCase()}</span><b>${l === LNG ? ICON.ok : ''}</b></button>`).join('')}</div><button class="btn ghost" id="mBack">${tr('Назад')}</button></div>`;
  $('mBack').onclick = $('sX').onclick = () => { uiSnd('back'); settings(from); }; fitHd(host);
  host.querySelectorAll('[data-l]').forEach(b => { b.onclick = () => { if (b.dataset.l === LNG) { uiSnd('back'); settings(from); } else { LS.set('lang', b.dataset.l);
    try { const u = new URL(location.href); if (u.searchParams.has('lang')) { u.searchParams.set('lang', b.dataset.l); history.replaceState(history.state, '', u); } } catch {}   // пришёл по ссылке с ?lang= — она главнее выбора: без замены страница перезагружалась на прежнем языке
    reloadKeep(from); } }; });
}
// проба громкости после ползунка. В паузе основной звук стоит (AC.suspend), поэтому проба идёт через свой маленький контекст с теми же множителями
let probeAC = null; const probeBuf = {};
function probe(k) {
  const name = k === 'voice' ? 'p01' : k === 'amb' ? 'rain' : 'click', cut = k === 'amb' ? 1.6 : 1.2;
  if (AC && AC.state === 'running') { play(name, k === 'amb' ? { to: [[uAmb, .2]], cut } : { bus: k === 'voice' ? 'voice' : 'sfx', cut }); return; }
  const Ctx = window.AudioContext || window.webkitAudioContext; if (!Ctx || !rawSnd[name]) return;
  if (!probeAC) probeAC = new Ctx();
  const go = () => {
    const s = probeAC.createBufferSource(), g = probeAC.createGain(), t = probeAC.currentTime;
    const lv = (LS.get('mute', false) ? 0 : VOL.all) * (k === 'all' ? .8 : k === 'voice' ? (voiceOn() ? VOL.voice : 0) * ((TIM[name] || {}).g || 1) : k === 'amb' ? VOL.amb * .2 : VOL.sfx * .8);
    s.buffer = probeBuf[name]; g.gain.setValueAtTime(lv, t); g.gain.setValueAtTime(lv, t + cut - .1); g.gain.linearRampToValueAtTime(0, t + cut); s.connect(g); g.connect(probeAC.destination); s.start(t); s.stop(t + cut + .01);
  };
  if (probeBuf[name]) go(); else probeAC.decodeAudioData(rawSnd[name].slice(0), d => { probeBuf[name] = d; go(); }, () => {});
}
function setVoice(on) { LS.set('voice', on); applyVol(); }
function setMute(m) { LS.set('mute', m); applyVol(); $('bMute').classList.toggle('muted', m); }
function applyVol() {   // громкости игрока → узлы звука (мягко, без щелчков)
  if (!AC) return; const m = LS.get('mute', false) ? 0 : VOL.all, v = voiceOn() ? VOL.voice : 0, at = AC.currentTime;
  for (const [n, x] of [[outG, m], [directG, VOL.sfx], [uMus, VOL.mus], [uSfx, VOL.sfx], [sfxRev, VOL.sfx], [sfxLow, VOL.sfx], [uAmb, VOL.amb], [bedLow, VOL.amb], [voiceOut, v], [voiceRev, v]]) if (n) n.gain.setTargetAtTime(x, at, .03);
}
async function begin(id) {
  unlockAudio(); goFull();   // до любого await, пока идёт нажатие
  if (id === 'intro') { for (const k in flags) delete flags[k]; LS.set('flags', {}); falseAlarm = false; lastTime = '02:55'; shk = null; LS.set('shk', null); LS.set('shk0', null); run = runNew(); runSave(); }
  else { const t = LS.get('saveT', null); if (t) { lastTime = t; clockEl.textContent = t; clockEl.classList.remove('hide'); } }   // «Продолжить»: время ночи того места (сцена осмотра своего не знает)
  uiEl.innerHTML = `<div class="screen"><small>${tr('Загрузка звука…')}</small></div>`;
  await Promise.race([initAudio(), new Promise(r => setTimeout(r, 8000))]);   // звук не успел — играем дальше, без зависания
  uiEl.innerHTML = ''; startScene(id);
}
/* ---------- финал концовки: последний кадр живёт дальше (медленный наезд, темнеет), поверх — название по буквам и строка; потом журнал ночи ---------- */
function finale(th) {
  const last = [...scene.beats].reverse().find(b => b.img), img = th.bg || (last && last.img);
  if (!img) return endScreen(Object.assign({}, th, { fin: 1 }));
  const c = th.bg ? (th.bgCam || [.5, .5, 1, 1.08]) : (last.cams || [[.5, .5, 1, 1.1]]).slice(-1)[0], z = th.bg ? c[2] : c[3];
  const fin = { img, fx: th.bg ? th.bgFx : last.fx, label: th.bg ? undefined : last.label, hud: th.bg ? undefined : last.hud, cams: [[c[0], c[1], z, th.bg ? c[3] : z * 1.08]], auto: true, fin: 1, pad: 8.5 };
  scene = Object.assign({}, scene, { beats: [...scene.beats, fin], then: Object.assign({}, th, { fin: 1 }) });   // копия: сценарий не трогаем
  clockEl.classList.add('hide'); $('hud').classList.add('hide'); hideSms();
  music(BUF['end_' + th.ending] || rawSnd['end_' + th.ending] ? 'end_' + th.ending : 'drone'); play('boom', { gain: .45 });
  bi = scene.beats.length - 2; nextBeat();
  uiEl.innerHTML = `<div class="screen fin" id="fin"><h1>${[...tr(th.title)].map((ch, i) => ch === ' ' ? ' ' : `<span style="animation-delay:${(.5 + i * .08).toFixed(2)}s">${escH(ch)}</span>`).join('')}</h1>
    <p>${escH(tr(th.sub))}</p><small>${tr('Нажми, чтобы продолжить')}</small></div>`;
  $('fin').onclick = () => { if (mode === 'play' && beat && beat.fin) { play('click'); nextBeat(); } };
}
// журнал ночи: страница журнала охраны — время в игре, часы смены, записи, решения, чем кончилось; штамп концовки; к выбору / заново / меню
function endScreen(th) {
  clockEl.classList.add('hide'); $('hud').classList.add('hide'); hideSms();
  mode = 'end'; const got = new Set(LS.get('endings', [])), fresh = !got.has(th.ending); got.add(th.ending); LS.set('endings', [...got]); LS.set('save', null); runSave();
  if (DESK && DESK.ach) { DESK.ach('END_' + String(th.ending).toUpperCase()); DESK.ach('NIGHT_DONE'); }   // достижения Steam (tools/desktop: «все концовки» main.js считает сам)
  if (!th.fin) play('boom'); music(BUF['end_' + th.ending] || rawSnd['end_' + th.ending] ? 'end_' + th.ending : 'drone');
  const fork = [...run.picks].reverse().find(p => forkAt(p.sc)), my = gen;
  const mins = Math.max(1, Math.round(run.sec / 60)), dur = mins >= 60 ? trf('{h} ч {m} мин', { h: Math.floor(mins / 60), m: mins % 60 }) : trf('{m} мин', { m: mins });
  const rows = [['Время в игре', dur], ['Часов смены отстоял', run.hrs.length], ['Странностей замечено', run.ok], ['Записей в журнале', run.rec], ['Пропущено', run.miss]];
  let d = .4; const L = () => `style="--d:${(d += .3).toFixed(2)}s"`;
  const page = `<div class="jr-page">
      <div class="jr-hd ln" ${L()}><b>${tr('ЖУРНАЛ ДЕЖУРСТВ')}</b><span>${tr('БЦ «Горизонт» · пост охраны · ночь 28→29 октября')}</span></div>
      ${rows.map(([k, v]) => `<div class="jr-row ln" ${L()}><span>${tr(k)}</span><b>${v}</b></div>`).join('')}
      ${run.picks.length ? `<div class="jr-sub ln" ${L()}>${tr('Решения за ночь')}</div><ol>${run.picks.map(p => `<li class="ln" ${L()}>${escH(pickA(p))}</li>`).join('')}</ol>` : ''}
      <div class="jr-end ln" ${L()}><b>${gameClock(false)} — ${escH(tr(th.title))}</b><span>${escH(tr(th.sub))}</span></div>
      <div class="jr-stamp" style="--d:${(d += .7).toFixed(2)}s">${escH(tr(S.endings[th.ending]))}${fresh ? `<i>${tr('новая концовка')}</i>` : ''}</div></div>`;
  uiEl.innerHTML = `<div class="screen jr">${page}<div class="jr-side">${gallery()}
      ${fork ? `<button class="btn" id="eBack">${tr('Вернуться к выбору')}<small>${escH(pickQ(fork))}</small></button>` : ''}
      <button class="btn${fork ? ' ghost' : ''}" id="eAgain">${tr('Начать заново')}</button><button class="btn ghost" id="eMenu">${tr('Меню')}</button>${DESK ? '' : supportBtn() + sponsorsHtml()}</div></div>`;
  // строки ложатся по очереди под скрип ручки, в конце — штамп
  uiEl.querySelectorAll('.jr .ln').forEach((el, i) => { if (i % 2 === 0) setTimeout(() => { if (mode === 'end' && gen === my) play('pen', { gain: .22, cut: .6 }); }, parseFloat(el.style.getPropertyValue('--d')) * 1000); });
  setTimeout(() => { if (mode === 'end' && gen === my) { play('bolt', { gain: .45, cut: .45 }); buzz(30); } }, (d + .1) * 1000);
  if (fork) $('eBack').onclick = () => { goFull(); uiEl.innerHTML = ''; startScene(fork.sc, true); };
  $('eAgain').onclick = () => begin('intro'); $('eMenu').onclick = menu;
}
function demoEnd() {
  clockEl.classList.add('hide'); $('hud').classList.add('hide'); hideSms();
  mode = 'end'; LS.set('save', null); music('tension');
  uiEl.innerHTML = `<div class="screen over demo"><h1>${tr('ОТКРОЕШЬ ЕЙ?')}</h1>
    <p>${tr('Здесь кончается бесплатная часть — у стеклянной двери, в 03:30.')}<br>${tr('В полной версии ночь идёт до шести: что будет, если открыть — и если нет, подвал, ещё два часа смены, четыре концовки.')}</p>
    ${buyBtn('dBuy', 'btn')}<button class="btn ghost" id="dMenu">${tr('Меню')}</button>
    <small>${tr('Полная версия скачивается на странице игры и работает без интернета.')}</small></div>`;
  $('dMenu').onclick = menu;
}
function overScreen(th) {
  clockEl.classList.add('hide'); $('hud').classList.add('hide'); hideSms();
  mode = 'end'; const ov = new Set(LS.get('overs', [])), oi = S.overs.indexOf(th.over); ov.add(OVER0[sceneId] || (oi >= 0 ? OVERS0[oi] : th.over)); LS.set('overs', [...ov]);
  if (DESK && DESK.ach && oi >= 0) DESK.ach('DEATH_' + String(oi + 1).padStart(2, '0'));   // смерть №… по списку S.overs (на любом языке тот же номер)
  music(null); if (hushOn) hush(false);   // тишина финала (hush) не переходит на экран смерти и в повтор
  // обрыв в черноту, секунда темноты (звук смерти ещё звучит), потом название; за ним из темноты проступает последний кадр — застывший
  overBg = { t0: now(), st: lastShotT }; setTimeout(() => { const s = uiEl.querySelector('.screen.over'); if (s) s.style.pointerEvents = ''; }, 800);
  uiEl.innerHTML = `<div class="screen over" style="pointer-events:none;animation:fadein 1s .8s both"><h1>${th.over}</h1><p>${th.sub}<br>${tr('Ты не дожил до утра.')}</p>
    <button class="btn" id="oRetry">${tr('Попробовать снова')}</button><button class="btn ghost" id="oMenu">${tr('Меню')}</button>
    <small>${trf('Смертей найдено: {c} из {d}', { c: oversGot().size, d: S.overs.length })}</small></div>`;
  // «Попробовать снова» — с места решения (мини-игра, выбор, обход), а не с начала сцены: кат-сцены второй раз не смотрим (владелец 24 окт)
  $('oRetry').onclick = () => { goFull(); uiEl.innerHTML = ''; let r = th.retry === '@save' ? (LS.get('save', null) || S.start) : th.retry; if (!S.scenes[r]) r = S.start;
    const sc = S.scenes[r], t2 = sc.then || {}, dec = (t2.choice || t2.mg || t2.explore || t2.talk) && sc.beats.length > 1; startScene(r, dec ? true : undefined); }; $('oMenu').onclick = menu;
  if (matchMedia('(pointer: fine)').matches) $('oRetry').focus({ preventScroll: true });   // ПК: Enter — снова, Esc — меню
}

/* ---------- рисование ---------- */
const noise = document.createElement('canvas'); noise.width = noise.height = 256;
{ const n = noise.getContext('2d'), d = n.createImageData(256, 256); for (let i = 0; i < d.data.length; i += 4) { const v = Math.random() * 255; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; } n.putImageData(d, 0, 0); }
let noisePat = null;
let drops = Array.from({ length: 140 }, () => ({ x: Math.random() * W, y: Math.random() * H, v: 1800 + Math.random() * 1400, l: 40 + Math.random() * 70 }));
// кадр камеры наблюдения обновляется рывками — 12 кадров в секунду, как настоящий регистратор: между кадрами что-то успевает сдвинуться
const heldC = document.createElement('canvas'); heldC.width = 1080; heldC.height = 1920; const heldG = heldC.getContext('2d'); let heldT = -9, heldK = '';
function heldShot(id, o, T, key) {
  if (T - heldT > 1 / 12 || key !== heldK || T < heldT) { heldT = T; heldK = key; shot(id, o, heldG); }
  g.drawImage(heldC, 0, 0);
}
// кадр 3D-мира; без WebGL — чёрный экран с подсказкой
let worldTried = false, menuT0 = 0, shotOff = 0, darkHold = false, settleQ = false, lastShotT = 0, overBg = null;   // settleQ — после перемотки (__ns.seek) мир сперва «доигрывает» позы; lastShotT — время последнего кадра (экран смерти держит его застывшим)
function shot(id, o, cx = g) {
  if (LAND && o.cx != null && !o._sx) { o.cx = scrX(o.cx); o._sx = 1; }   // точки наезда заданы для 9:16 — в 16:9 то же направление
  if (window.World && World.ok && World.draw(cx, W, H, id, Object.assign(o, { props: propsNow(), settle: settleQ }))) { lastDrawn = id; lastShotT = o.t || 0; settleQ = false; return true; }
  cx.fillStyle = '#000'; cx.fillRect(0, 0, W, H); if (!worldTried) return false;   // пока мир грузится — просто чёрный
  g.fillStyle = '#777'; g.font = '700 40px Inter, Arial'; g.textAlign = 'center'; g.fillText(tr('Нужен браузер с WebGL'), W / 2, H * .3); g.textAlign = 'left';
  return false;
}
const ease = t => t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
// рывок картинки: полоса строк съезжает вбок на один кадр
function tear(n = 1) {
  for (let i = 0; i < n; i++) { const y = Math.random() * H * .9, h = 30 + Math.random() * 110, dx = (Math.random() < .5 ? -1 : 1) * (18 + Math.random() * 46);
    g.drawImage(cv, 0, y, W, h, dx, y, W, h); g.fillStyle = 'rgba(235,240,235,.22)'; g.fillRect(0, y + h * (.3 + Math.random() * .4), W, 3); }
}
// экран камеры наблюдения: REC мигает раз в секунду, время идёт по часам ночи, уголки кадра; цвет даёт шейдер (cctv: 1)
let tearNext = 0;
function staticBox(b, T, k, a0 = .55, fig3d = false) {
  const x = b[0] * W, y = b[1] * H, w = b[2] * W, h = b[3] * H;
  g.save(); g.beginPath(); g.rect(x, y, w, h); g.clip();
  if (!noisePat) noisePat = g.createPattern(noise, 'repeat');
  g.globalAlpha = a0 + .35 * Math.min(1, k / 3); g.translate(Math.random() * 256, Math.random() * 256); g.fillStyle = noisePat; g.fillRect(x - 256, y - 256, w + 512, h + 512); g.setTransform(1, 0, 0, 1, 0, 0);
  if (k > 1.6 && !fig3d) { const a = Math.min(.75, (k - 1.6) / 2.4), cx = x + w / 2, cy = y + h * .42;   // силуэт: голова и плечи из более тёмной ряби (если в кадре нет настоящей фигуры)
    g.globalAlpha = a; g.fillStyle = '#050505'; g.beginPath(); g.ellipse(cx, cy, w * .13, h * .1, 0, 0, 7); g.fill(); g.beginPath(); g.ellipse(cx, cy + h * .3, w * .3, h * .22, 0, 0, 7); g.fill();
    if (k > 3.4) { g.fillStyle = '#ddd'; g.globalAlpha = Math.min(.8, (k - 3.4)); for (const s of [-1, 1]) { g.beginPath(); g.arc(cx + s * w * .045, cy - h * .01, w * .012, 0, 7); g.fill(); } } }
  for (let i = 0; i < 6; i++) { g.globalAlpha = .25; g.fillStyle = '#fff'; g.fillRect(x, y + Math.random() * h, w, 1 + Math.random() * 3); }
  g.restore();
}
// Жилец в помехах (странность stare): стоит в at; пока игрок смотрит на камеру, он рывками, под вспышку ряби, подходит к объективу (near). В 5 с — смерть (shiftTick)
const STARE_T = [0, 1.3, 2.6, 3.8], STARE_K = [0, .42, .76, 1];
const stareN = k => k < STARE_T[1] ? 0 : k < STARE_T[2] ? 1 : k < STARE_T[3] ? 2 : 3;
function stareSpot(d, k) { if (d.steps) return d.steps[stareN(k)]; const a = d.at, b = d.near || a, s = STARE_K[stareN(k)]; return [a[0] + (b[0] - a[0]) * s, (a[1] || 0) + ((b[1] || 0) - (a[1] || 0)) * s, a[2] + (b[2] - a[2]) * s]; }
// помехи по самой фигуре, а не прямоугольник: кадр под ним — крупными блоками (камера «не может его разобрать»), полосы строк срываются вбок,
// сверху рябь; края мягкие. Чем ближе он подошёл (n), тем мельче блоки и реже рябь — у объектива на миг виден совсем чётко
let glA = null, glB = null, glPat = null;
const hs1 = i => { const s = Math.sin(i * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };
function figGlitch(b, T, n, jump) {
  const x = Math.max(0, Math.floor(b[0] * W - b[2] * W * .15)), y = Math.max(0, Math.floor(b[1] * H)), w = Math.min(W - x, Math.ceil(b[2] * W * 1.3)), h = Math.min(H - y, Math.ceil(b[3] * H)); if (w < 6 || h < 6) return;
  const f = Math.floor(T * 12), clear = n === 3 && !jump && f % 10 < 2; if (clear) return;
  if (!glA) { glA = document.createElement('canvas'); glB = document.createElement('canvas'); }
  const px = Math.max(2, [11, 7, 4.5, 2.6][n] * US * (w / (W * .2) > 1 ? 1 : .7)), sw = Math.max(2, Math.round(w / px)), sh = Math.max(2, Math.round(h / px));
  glA.width = sw; glA.height = sh; glA.getContext('2d').drawImage(cv, x, y, w, h, 0, 0, sw, sh);
  glB.width = w; glB.height = h; const q = glB.getContext('2d'); q.imageSmoothingEnabled = false;
  const N = 9, amp = [.32, .22, .13, .07][n] * w;
  for (let i = 0; i < N; i++) { const off = (hs1(f * 17 + i) - .5) * 2 * amp * (hs1(f * 5 + i * 3) > .55 ? 1 : .25); q.drawImage(glA, 0, i * sh / N, sw, sh / N, off, i * h / N, w, h / N + 1); }
  if (!glPat) glPat = q.createPattern(noise, 'repeat');
  q.globalCompositeOperation = 'screen';   // снег светом: тёмное остаётся тёмным, а не сереет ровной кашей
  q.globalAlpha = jump ? 1 : [.75, .6, .45, .3][n]; q.save(); q.translate(-Math.random() * 256, -Math.random() * 256); q.fillStyle = glPat; q.fillRect(0, 0, w + 256, h + 256); q.restore();
  q.globalCompositeOperation = 'source-over'; q.globalAlpha = .3; q.fillStyle = '#fff'; for (let i = 0; i < 5; i++) q.fillRect(0, Math.random() * h, w, 1 + Math.random() * 2 * US);
  // край — рваный, по строкам и по форме тела (голова, плечи, корпус, ноги), а не гладкий овал; сверху и снизу тает
  q.globalAlpha = 1; q.globalCompositeOperation = 'destination-in'; q.beginPath();
  const B = 30; for (let i = 0; i < B; i++) { const yy = (i + .5) / B, pr = yy < .13 ? .34 : yy < .19 ? .58 : yy < .56 ? .9 : yy < .64 ? .74 : .62;
    const hw = w / 2 * pr * (.72 + .5 * hs1(f * 31 + i * 7)), off = (hs1(f * 7 + i * 5) - .5) * w * .1; q.rect(w / 2 - hw + off, i * h / B, hw * 2, h / B + 1); }
  const gr = q.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(.07, '#fff'); gr.addColorStop(.9, '#fff'); gr.addColorStop(1, 'rgba(255,255,255,0)');
  q.fillStyle = gr; q.fill(); q.globalCompositeOperation = 'source-over';
  g.drawImage(glB, x, y);
}
function figBox(at) {   // прямоугольник помех вокруг фигуры у точки at (рост ~2,5 м): по её проекции на текущем кадре — совпадает в любой ориентации
  if (!window.World || !World.project) return null; const y = at[1] || 0, f = World.project([at[0], y + .02, at[2]]), h = World.project([at[0], y + 2.55, at[2]]); if (!f[2] && !h[2]) return null;
  const y0 = Math.min(f[1], h[1]), y1 = Math.max(f[1], h[1]), w = (y1 - y0) * .42 * H / W, cx = (f[0] + h[0]) / 2; return [cx - w / 2, y0 - .012, w, y1 - y0 + .024];
}
function noisePatch(N, T) {   // помехи, привязанные к месту в мире («там, где она стояла, были только помехи»): прямоугольник w×h м вокруг точки at
  if (!window.World || !World.project) return; const P = N.at, c = World.project(P); if (!c[2]) return;
  const l = World.project([P[0] - N.w / 2, P[1], P[2]]), r = World.project([P[0] + N.w / 2, P[1], P[2]]), tp = World.project([P[0], P[1] + N.h / 2, P[2]]), bt = World.project([P[0], P[1] - N.h / 2, P[2]]);
  const x0 = Math.min(l[0], r[0]), x1 = Math.max(l[0], r[0]), y0 = Math.min(tp[1], bt[1]), y1 = Math.max(tp[1], bt[1]), j = (Math.random() - .5) * .012;
  staticBox([x0 + j, y0, x1 - x0, y1 - y0], T, 0, .86);
  if (Math.random() < .35) { const yy = (y0 + Math.random() * (y1 - y0)) * H; g.drawImage(cv, 0, yy, W, 18 * US, (Math.random() - .5) * 60 * US, yy, W, 18 * US); }   // строка рвётся вбок
}
function cctvHud(label, T, rw) {   // rw = { tag, clock } — запись на перемотке
  if (T > tearNext) { tearNext = T + 2.2 + Math.random() * 4.5; tear(1); }
  grain(.12);
  g.save(); g.textBaseline = 'top'; g.shadowColor = 'rgba(0,0,0,.9)'; g.shadowBlur = 6;
  g.strokeStyle = 'rgba(235,240,235,.55)'; g.lineWidth = 4 * US; const m = 46 * US, L = 64 * US;
  g.beginPath(); for (const [x, y, sx, sy] of [[m, m, 1, 1], [W - m, m, -1, 1], [m, H - m, 1, -1], [W - m, H - m, -1, -1]]) { g.moveTo(x + sx * L, y); g.lineTo(x, y); g.lineTo(x, y + sy * L); } g.stroke();
  g.scale(US, US); g.font = '700 40px "Courier New", monospace'; g.fillStyle = 'rgba(235,240,235,.9)';
  if ((T % 1) < .5) { g.fillStyle = '#e8262e'; g.beginPath(); g.arc(98, 222, 13, 0, 7); g.fill(); g.fillStyle = 'rgba(235,240,235,.9)'; }
  g.fillText(rw ? rw.tag : 'REC', 124, 204);
  if (!rw && window.World && World.ir) { g.fillStyle = 'rgba(235,240,235,.75)'; g.fillText('IR', 250, 204); g.fillStyle = 'rgba(235,240,235,.9)'; }   // темно — камера в ИК
  if (label) g.fillText(label, 84, 258);
  g.font = '700 34px "Courier New", monospace'; g.fillText(locDate(rw && rw.date || hudDate()) + '  ' + (rw ? rw.clock : gameClock()), 84, 310);
  if (mode === 'shift' && shf && !rw) {   // микрофон камеры: что-то слышно — столбики прыгают
    shf.mic = (shf.mic || 0) * .94; const lv = Math.min(1, .1 + Math.random() * .1 + shf.mic * (.75 + Math.random() * .25));
    g.font = '700 26px "Courier New", monospace'; g.fillText('MIC', 84, 356);
    for (let i = 0; i < 12; i++) { g.fillStyle = i / 12 < lv ? (i > 8 ? '#e8262e' : i > 5 ? '#e8c22e' : 'rgba(150,235,160,.9)') : 'rgba(235,240,235,.18)'; g.fillRect(150 + i * 15, 360, 10, 18); }
  }
  g.restore();
}
let hudRef = null, hudT0 = 0;
function hudDate() { return +lastTime.slice(0, 2) < 12 ? '29.10.2026' : '28.10.2026'; }   // ночь с 28 на 29 октября (запись Итана в журнале — 28.10)
// дата на записи камер — как пишут в стране игрока: США 10-29-2026, Азия 2026/10/29, Корея 2026.10.29, юг Европы 29/10/2026, остальные 29.10.2026
function locDate(d) { const [dd, mm, yy] = String(d).split('.'); if (!yy) return d;
  return LNG === 'en' ? `${mm}-${dd}-${yy}` : LNG === 'ja' || LNG === 'zh' ? `${yy}/${mm}/${dd}` : LNG === 'ko' ? `${yy}.${mm}.${dd}` : /^(es|pt|fr|it)$/.test(LNG) ? `${dd}/${mm}/${yy}` : d; }
function grain(a) { g.save(); g.globalAlpha = a; g.globalCompositeOperation = 'overlay'; const ox = Math.random() * 256, oy = Math.random() * 256; g.translate(-ox, -oy); g.fillStyle = noisePat || (noisePat = g.createPattern(noise, 'repeat')); g.fillRect(0, 0, W + 256, H + 256); g.restore(); }
// старое фото (fx 'photo'): выцветшие тёплые цвета, царапины; на общем плане — белая рамка и блик стекла на столе.
// P.smudge — мировая точка лица, стёртого пальцем (светлое пятно); P.backCam — план, где снимок перевёрнут: оборот с подписью P.back
const h1 = i => { const x = Math.sin(i * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
function photoFx(P, ci, z) {
  if (P.backCam != null && ci === P.backCam) {
    g.fillStyle = '#e8e1cf'; g.fillRect(0, 0, W, H);
    for (let i = 0; i < 500; i++) { g.fillStyle = `rgba(120,100,70,${.07 * h1(i)})`; g.fillRect(h1(i + 1) * W, h1(i + 2) * H, 2, 2); }
    g.save(); g.translate(W / 2, H * .47); g.rotate(-.05); g.fillStyle = '#1c2a6a'; g.textAlign = 'center'; g.textBaseline = 'middle';
    const L = tr(P.back || '').split('\n'), fs = Math.min(W * .1, H * .075); g.font = `italic 600 ${fs}px Georgia, "Times New Roman", serif`;
    L.forEach((s, i) => g.fillText(s, 0, (i - (L.length - 1) / 2) * fs * 1.4)); g.restore(); return;
  }
  g.save();
  g.globalCompositeOperation = 'saturation'; g.fillStyle = 'rgba(128,128,128,.5)'; g.fillRect(0, 0, W, H);   // выцвело
  g.globalCompositeOperation = 'multiply'; g.fillStyle = '#f0d8b2'; g.fillRect(0, 0, W, H);   // пожелтело
  g.globalCompositeOperation = 'screen'; g.fillStyle = 'rgba(60,42,26,.45)'; g.fillRect(0, 0, W, H);   // чёрное стало коричневым
  g.restore();
  if (P.smudge && window.World && World.project) { const a = World.project(P.smudge), b = World.project([P.smudge[0], P.smudge[1] + .12, P.smudge[2]]);
    if (a[2]) { const x = a[0] * W, y = a[1] * H, r = Math.hypot((b[0] - a[0]) * W, (b[1] - a[1]) * H);   // затёрто пальцем: светлое пятно, по кругу мелкие царапины
      for (let i = 0; i < 6; i++) {   // неровное пятно: несколько затёртых кругов, по краям сквозь них чуть видно лицо
        const bx = x + (h1(i + 90) - .5) * r * .5, by = y + (h1(i + 95) - .5) * r * .6, br = r * (.55 + .35 * h1(i + 99));
        const gr = g.createRadialGradient(bx, by, br * .15, bx, by, br); gr.addColorStop(0, 'rgba(236,228,210,.6)'); gr.addColorStop(.6, 'rgba(230,220,200,.45)'); gr.addColorStop(1, 'rgba(225,214,192,0)');
        g.fillStyle = gr; g.beginPath(); g.ellipse(bx, by, br * .85, br, (h1(i + 3) - .5) * .8, 0, 7); g.fill(); }
      g.strokeStyle = 'rgba(150,130,100,.35)'; g.lineWidth = Math.max(1, r * .025); g.beginPath();
      for (let i = 0; i < 28; i++) { const an = h1(i) * 6.28, rr = r * (.2 + .6 * h1(i + 50)); g.moveTo(x + Math.cos(an) * rr, y + Math.sin(an) * rr); g.lineTo(x + Math.cos(an + .45) * rr * 1.08, y + Math.sin(an + .45) * rr * 1.08); }
      g.stroke(); } }
  g.strokeStyle = 'rgba(255,250,235,.22)'; g.lineWidth = 1; g.beginPath();
  for (let i = 0; i < 9; i++) { const x = h1(i + 7) * W, y = h1(i + 19) * H; g.moveTo(x, y); g.lineTo(x + (h1(i + 3) - .5) * W * .3, y + h1(i + 11) * H * .4); } g.stroke();
  if (z < 1.25) { const b = Math.min(W, H) * .045;
    g.fillStyle = '#ebe5d6'; g.fillRect(0, 0, W, b); g.fillRect(0, H - b, W, b); g.fillRect(0, 0, b, H); g.fillRect(W - b, 0, b, H);
    const gl = g.createLinearGradient(0, 0, W, H); gl.addColorStop(.3, 'rgba(255,255,255,0)'); gl.addColorStop(.42, 'rgba(255,255,255,.12)'); gl.addColorStop(.5, 'rgba(255,255,255,0)'); g.fillStyle = gl; g.fillRect(0, 0, W, H); }
}
function vignette() { const r = g.createRadialGradient(W / 2, H / 2, H * .25, W / 2, H / 2, H * .75); r.addColorStop(0, 'rgba(0,0,0,0)'); r.addColorStop(1, 'rgba(0,0,0,.8)'); g.fillStyle = r; g.fillRect(0, 0, W, H); }
function phone(spec, t) {
  g.fillStyle = '#030405'; g.fillRect(0, 0, W, H);
  g.save(); g.translate(W / 2 - 540 * US, 0); g.scale(US, US); phoneIn(typeof spec === 'string' ? { kind: spec } : spec, t); g.restore(); grain(.08);
}
// заставка — рисунок Лили (ей шесть): Итан, она и пёс Бублик, солнце, трава; мелками, подписи детским почерком. Рисуется кодом.
let drawingC = null;
function drawing() {   // заставка телефона: детский рисунок Лили (ей шесть) — Итан, она и пёс Бублик. Фигуры простые и ясные: голова, улыбка, закрашенная одежда
  if (drawingC) return drawingC;
  const c = drawingC = document.createElement('canvas'); c.width = 600; c.height = 1060; const x = c.getContext('2d');
  let sd = 7; const R = () => ((sd = (sd * 16807) % 2147483647) / 2147483647);
  x.fillStyle = '#f1ead9'; x.fillRect(0, 0, 600, 1060);
  for (let i = 0; i < 2600; i++) { x.fillStyle = `rgba(${R() < .5 ? '120,110,90' : '255,255,250'},${.05 + R() * .06})`; x.fillRect(R() * 600, R() * 1060, 2, 2); }   // зерно бумаги
  const crayon = (col, w, pts, n = 2) => { for (let k = 0; k < n; k++) { x.strokeStyle = col; x.globalAlpha = .7; x.lineWidth = w * (.8 + R() * .4); x.lineCap = 'round'; x.lineJoin = 'round'; x.beginPath();
    pts.forEach(([px, py], i) => { const jx = px + (R() - .5) * w * .4, jy = py + (R() - .5) * w * .4; i ? x.lineTo(jx, jy) : x.moveTo(jx, jy); }); x.stroke(); } x.globalAlpha = 1; };
  const oval = (cx, cy, rx, ry) => { const p = []; for (let i = 0; i <= 28; i++) { const a = i / 28 * Math.PI * 2 + .3; p.push([cx + Math.cos(a) * rx * (1 + (R() - .5) * .06), cy + Math.sin(a) * ry * (1 + (R() - .5) * .06)]); } return p; };
  // закрашено «как мелком»: штрихи наискосок внутри контура, два прохода
  const paint = (col, pts) => { x.save(); x.beginPath(); pts.forEach(([px, py], i) => i ? x.lineTo(px, py) : x.moveTo(px, py)); x.closePath(); x.clip();
    const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]), x0 = Math.min(...xs) - 20, x1 = Math.max(...xs) + 20, y0 = Math.min(...ys) - 20, y1 = Math.max(...ys) + 20;
    x.strokeStyle = col; x.lineCap = 'round';
    for (const [dx, dy] of [[1, 1], [1, -.6]]) for (let s = -(x1 - x0) - (y1 - y0); s < (x1 - x0) + (y1 - y0); s += 7) {
      x.globalAlpha = .45 + R() * .3; x.lineWidth = 6 + R() * 3; x.beginPath(); x.moveTo(x0 + s + (R() - .5) * 4, y0); x.lineTo(x0 + s + dx * (y1 - y0) / dy + (R() - .5) * 4, y1); x.stroke(); }
    x.globalAlpha = 1; x.restore(); };
  const shape = (col, line, w, pts) => { paint(col, pts); crayon(line, w, [...pts, pts[0]]); };
  const face = (cx, cy, r, skin, line) => { shape(skin, line, 5, oval(cx, cy, r, r * 1.08));
    x.fillStyle = '#222'; x.beginPath(); x.arc(cx - r * .36, cy - r * .12, r * .09, 0, 7); x.arc(cx + r * .36, cy - r * .12, r * .09, 0, 7); x.fill();
    crayon('#c03030', 4, [[cx - r * .4, cy + r * .35], [cx - r * .15, cy + r * .55], [cx + r * .15, cy + r * .55], [cx + r * .4, cy + r * .35]], 2); };   // улыбка
  // солнце в углу, облако, трава
  shape('#f5c21c', '#e0a010', 6, oval(500, 135, 56, 56));
  for (let i = 0; i < 11; i++) { const a = i / 11 * Math.PI * 2 + .2; crayon('#e0a010', 6, [[500 + Math.cos(a) * 72, 135 + Math.sin(a) * 72], [500 + Math.cos(a) * 108, 135 + Math.sin(a) * 108]], 2); }
  crayon('#8fb4d8', 6, [[70, 190], [95, 165], [130, 160], [160, 172], [190, 160], [222, 175], [228, 200], [200, 212], [120, 214], [70, 206], [70, 190]], 2);   // облако
  for (let i = 0; i < 52; i++) { const gx = i * 11.5 + R() * 6; crayon('#3f9a3a', 5, [[gx, 1012], [gx + (R() - .5) * 16, 955 + R() * 25]], 1); }
  crayon('#3f9a3a', 9, [[0, 1000], [600, 990]], 3);
  x.save(); x.translate(42, 158); x.scale(.86, .86);   // фигуры — под часами и уведомлениями
  // Итан — высокий: тёмные волосы, синяя футболка, джинсы
  shape('#2f5fb0', '#1f3f80', 6, [[112, 525], [188, 525], [206, 560], [204, 705], [96, 705], [94, 560]]);   // футболка
  shape('#3b4a78', '#26305a', 6, [[100, 705], [200, 705], [196, 900], [160, 900], [152, 760], [148, 760], [140, 900], [104, 900]]);   // джинсы
  crayon('#333', 9, [[98, 906], [142, 906]], 2); crayon('#333', 9, [[156, 906], [202, 906]], 2);   // ботинки
  crayon('#f0c8a0', 11, [[100, 560], [60, 660]], 2); crayon('#f0c8a0', 11, [[200, 560], [255, 640]], 2);   // руки; правая тянется к руке Лили
  face(150, 470, 46, '#f0c8a0', '#8a5a3a'); shape('#3a2a1a', '#2a1a0a', 5, [[104, 452], [112, 420], [150, 406], [188, 420], [196, 452], [176, 436], [150, 430], [124, 436]]);   // волосы
  // Лили — маленькая, косички с бантиками, жёлтое платье, руки в стороны (одна — в руке Итана)
  shape('#f2c81c', '#d8a010', 6, [[270, 660], [330, 660], [368, 820], [232, 820]]);   // платье
  crayon('#f0c8a0', 9, [[272, 670], [255, 640]], 2); crayon('#f0c8a0', 9, [[328, 670], [372, 700]], 2);   // руки
  crayon('#333', 8, [[285, 822], [282, 900]], 2); crayon('#333', 8, [[315, 822], [318, 900]], 2); crayon('#333', 8, [[270, 906], [296, 906]], 2); crayon('#333', 8, [[306, 906], [334, 906]], 2);
  face(300, 610, 38, '#f0c8a0', '#8a5a3a'); shape('#7a4a20', '#5a3210', 5, [[262, 596], [270, 572], [300, 562], [330, 572], [338, 596], [320, 586], [300, 582], [280, 586]]);   // чёлка
  crayon('#7a4a20', 9, [[262, 602], [246, 650]], 2); crayon('#7a4a20', 9, [[338, 602], [354, 650]], 2); crayon('#e03a5a', 5, [[238, 650], [254, 642], [250, 658]], 2); crayon('#e03a5a', 5, [[346, 650], [362, 642], [358, 658]], 2);   // косички и бантики
  // Бублик — рыжий, круглый, уши висят, хвост бубликом, язык
  shape('#c8812e', '#7a4a1a', 6, oval(462, 822, 82, 50));                                         // туловище
  for (const lx of [412, 444, 486, 514]) crayon('#7a4a1a', 9, [[lx, 858], [lx, 905]], 2);     // лапы
  crayon('#7a4a1a', 8, [[386, 806], [364, 782], [372, 758], [394, 766], [388, 788]], 2);      // хвост закорючкой
  shape('#c8812e', '#7a4a1a', 6, oval(548, 762, 40, 37));                                      // голова
  shape('#8a5218', '#5a3210', 5, [[514, 742], [528, 740], [524, 790], [506, 786]]); shape('#8a5218', '#5a3210', 5, [[568, 740], [584, 742], [590, 788], [572, 790]]);   // уши
  x.fillStyle = '#222'; x.beginPath(); x.arc(538, 752, 4.5, 0, 7); x.arc(560, 752, 4.5, 0, 7); x.fill(); x.beginPath(); x.arc(550, 772, 7, 0, 7); x.fill();   // глаза, нос
  crayon('#e04a6a', 6, [[550, 784], [552, 798]], 2); crayon('#c03030', 5, [[512, 796], [548, 806], [584, 796]], 2);   // язык, ошейник
  // сердечко и подписи печатными буквами
  crayon('#e03a5a', 7, [[300, 440], [278, 412], [290, 392], [300, 408], [310, 392], [322, 412], [300, 440]], 3);
  x.fillStyle = '#2b2b8a'; x.font = '700 44px "Comic Sans MS", "Segoe Print", "Chalkboard SE", cursive'; x.textAlign = 'center';
  const word = (s, px, py, rot) => { x.save(); x.translate(px, py); x.rotate(rot); x.globalAlpha = .85; x.fillText(tr(s), 0, 0); x.restore(); };
  word('ИТАН', 150, 382, -.05); word('Я', 300, 542, .08); word('БУБЛИК', 470, 692, -.07);
  crayon('#2b2b8a', 5, [[470, 702], [478, 724]], 2);
  x.restore();
  return c;
}
function phoneIn(P, t) {   // экран телефона в координатах вертикального холста 1080×1920
  const W = 1080, kind = P.kind;
  const x = 240, y = 230, w = 600, h = 1060; g.fillStyle = '#111'; rr(x - 20, y - 20, w + 40, h + 40, 70); g.fillStyle = '#0b0d12'; rr(x, y, w, h, 54);
  g.textAlign = 'center'; g.fillStyle = '#fff';
  if (kind === 'lock') {   // заставка: рисунок, часы, уведомления
    g.save(); g.beginPath(); g.roundRect ? g.roundRect(x, y, w, h, 54) : g.rect(x, y, w, h); g.clip(); g.drawImage(drawing(), x, y, w, h);
    g.fillStyle = 'rgba(0,0,0,.18)'; g.fillRect(x, y, w, 330); g.restore();
    g.fillStyle = '#fff'; g.font = '300 150px Inter, Arial'; g.fillText(P.time || '21:00', W / 2, y + 230); g.font = '500 34px Inter, Arial'; g.fillText(tr(P.date || 'среда, 28 октября'), W / 2, y + 285);
    (P.notes || []).forEach((n, i) => { if (t < (n.at ?? .6)) return; const k = clamp((t - (n.at ?? .6)) / .25, 0, 1), ny = y + 312 + i * 160 - (1 - k) * 40;
      g.globalAlpha = k; g.fillStyle = 'rgba(28,30,36,.93)'; rr(x + 24, ny, w - 48, 146, 30);
      g.textAlign = 'left'; g.fillStyle = '#9ab'; g.font = '600 26px Inter, Arial'; g.fillText(tr('СООБЩЕНИЯ') + ' · ' + tr(n.from || 'Лили'), x + 52, ny + 42);
      g.textAlign = 'right'; g.fillText(tr(n.ago || 'сейчас'), x + w - 52, ny + 42); g.textAlign = 'left';   // когда отправлено: «Отправлено минуту назад» (03:31) — так и написано в уведомлении
      g.fillStyle = '#fff'; g.font = '500 32px Inter, Arial'; wrapText(tr(n.text), x + 52, ny + 86, w - 104, 38, 2); g.globalAlpha = 1; g.textAlign = 'center'; });
  } else if (kind === 'msg') {
    g.font = '600 34px Inter, Arial'; g.fillStyle = '#9aa'; g.fillText(tr('Лили'), W / 2, y + 120);
    g.fillStyle = '#2b2f3a'; rr(x + 50, y + 420, w - 120, 190, 36); g.fillStyle = '#fff'; g.font = '600 40px Inter, Arial'; g.textAlign = 'left';
    g.fillText(tr('Не открывай им.'), x + 90, y + 500); if (t > 1.6) g.fillText(tr('Это не я.'), x + 90, y + 560);
    if (t > 2.4) { g.fillStyle = '#2b2f3a'; rr(x + 50, y + 650, w - 220, 120, 36); g.fillStyle = '#f66'; g.fillText(tr('Я тебя вижу'), x + 90, y + 725); }
  } else {   // call — входящий, out — исходящий (идут гудки), call2/talk — разговор идёт (таймер)
    const pulse = 1 + .06 * Math.sin(t * 8), nm = tr(P.name || 'Лили'), talk = kind === 'call2' || kind === 'talk';
    g.font = '700 70px Inter, Arial'; g.fillText(nm, W / 2, y + 330); g.font = '500 36px Inter, Arial'; g.fillStyle = '#9aa';
    g.fillText(talk ? '00:' + String(Math.floor(t) + (P.t0 ?? 8)).padStart(2, '0') : kind === 'out' ? tr('вызов') + '…'.slice(0, 1) : tr('мобильный · входящий'), W / 2, y + 400);
    if (kind === 'call') { g.fillStyle = '#2a3'; g.beginPath(); g.arc(x + w - 150, y + h - 200, 70 * pulse, 0, 7); g.fill(); }
    g.fillStyle = '#d33'; g.beginPath(); g.arc(kind === 'call' ? x + 150 : W / 2, y + h - 200, 70, 0, 7); g.fill();
  }
  g.textAlign = 'left';
}
// брызги крови на объектив: пятна с подтёками, всегда одни и те же для смерти (seed)
function splatBlood(g, W, H, k, seed) {
  const r = i => { const x = Math.sin(seed * 91.7 + i * 12.9898) * 43758.5453; return x - Math.floor(x); }, U = Math.min(W, H);
  g.save(); g.globalAlpha = .85 * k;
  for (let i = 0; i < 9; i++) { const x = r(i) * W, y = r(i + 20) * H * .8, rr = U * (.03 + r(i + 40) * .09);
    const gr = g.createRadialGradient(x, y, 0, x, y, rr); gr.addColorStop(0, 'rgba(70,0,0,.95)'); gr.addColorStop(.7, 'rgba(110,4,4,.8)'); gr.addColorStop(1, 'rgba(110,4,4,0)');
    g.fillStyle = gr; g.beginPath(); g.arc(x, y, rr, 0, 7); g.fill();
    g.fillStyle = 'rgba(90,2,2,.85)'; const dl = rr * (1.5 + r(i + 60) * 3) * k; g.fillRect(x - rr * .08, y, rr * .16, dl); }
  g.restore();
}
function wrapText(s, x0, y0, maxW, lh, maxL) { const cj = /^(zh|ja)$/.test(LNG), ws = cj ? [...s] : s.split(' '); let line = '', n = 0;   // китайский и японский без пробелов — перенос по знакам
  for (const wd of ws) { if (cj && !line && wd === ' ') continue; const tst = line ? line + (cj ? '' : ' ') + wd : wd; if (g.measureText(tst).width > maxW && line) { g.fillText(line, x0, y0 + n * lh); line = wd; if (++n >= maxL) return; } else line = tst; }
  if (line) g.fillText(line, x0, y0 + n * lh); }
function rr(x, y, w, h, r) { g.beginPath(); g.roundRect ? g.roundRect(x, y, w, h, r) : g.rect(x, y, w, h); g.fill(); }
// пред-кадр: застывший прошлый кадр; перед скримером он тонет в темноте, у fx 'glitch' — окно «соединение потеряно»
function preFrame(t, T) {
  const k = 1 + t / pre;   // 0 → 1
  g.drawImage(frozenC, 0, 0);
  if (beat.fx !== 'glitch') { g.fillStyle = `rgba(0,0,0,${Math.min(.93, k * 1.15)})`; g.fillRect(0, 0, W, H); return; }
  g.fillStyle = 'rgba(0,0,0,.42)'; g.fillRect(0, 0, W, H);
  if (k < .12) tear(3);
  g.save(); g.translate(W / 2 - 540 * US, H * .38 - 1920 * .38 * US); g.scale(US, US); glitchBox(T); g.restore();
}
function glitchBox(T) {
  const W = 1080, H = 1920, bw = 820, bh = 290, bx = (W - bw) / 2, by = H * .38;
  g.fillStyle = 'rgba(12,14,16,.92)'; g.fillRect(bx, by, bw, bh); g.fillStyle = '#c8202a'; g.fillRect(bx, by, bw, 10);
  g.strokeStyle = 'rgba(230,226,214,.35)'; g.lineWidth = 2; g.strokeRect(bx + 1, by + 1, bw - 2, bh - 2);
  const cx = bx + 110, cy = by + bh / 2 + 5, st = Math.floor(T * 12) % 8;   // крутилка ожидания
  for (let i = 0; i < 8; i++) { const a = i / 8 * Math.PI * 2; g.fillStyle = `rgba(230,226,214,${i === st ? .95 : .22})`; g.fillRect(cx + Math.cos(a) * 34 - 6, cy + Math.sin(a) * 34 - 6, 12, 12); }
  g.textBaseline = 'middle'; g.fillStyle = '#e6e2d6'; g.font = '700 46px "Courier New", monospace'; g.fillText(tr('СОЕДИНЕНИЕ ПОТЕРЯНО'), bx + 190, by + 118);
  g.font = '600 32px "Courier New", monospace'; g.fillStyle = 'rgba(230,226,214,.62)'; g.fillText(tr('переподключение') + '...'.slice(0, 1 + Math.floor(T * 4) % 3), bx + 190, by + 178);
  g.textBaseline = 'alphabetic';
}
let lastSubKey = '';
function plate(who) {
  const sp = SPK[who] || SPK['ИТАН']; dlgEl.style.setProperty('--spk', sp.c);
  dlgEl.className = (sp.cls || '') + (holding ? ' wait' : ''); dlgEl.querySelector('.nm').innerHTML = tr(who) + (sp.sub ? `<small>${tr(sp.sub)}</small>` : '');
}
function say(who, text, k) {   // печатающийся текст
  const key = who + '|' + k + '|' + text; if (key === lastSubKey) return; lastSubKey = key;
  plate(who); dlgEl.querySelector('.ln').textContent = text.slice(0, k);
}
const chunkCache = {};
function chunks(k, w) {   // куски из таймингов без знаков препинания: берём те же слова из полного текста реплики
  if (chunkCache[k]) return chunkCache[k];
  if (/^(zh|ja)$/.test(LNG)) return (chunkCache[k] = w.map(x => x[1]));   // без пробелов: куски тайминга (build.js cjkWords) уже со знаками препинания
  // слово полного текста = столько же слов, сколько в нём частей без знаков («05:40», «кто-то» — две, как режет voice.py); тире прилипает к соседнему куску
  const toks = ((TIM[k] || {}).t || '').split(/\s+/).filter(Boolean).map(s => [s, s.replace(/[.,:;—?!…«»"()\-¿¡„“”]/g, ' ').split(/\s+/).filter(Boolean).length]);
  let j = 0, debt = 0;
  const out = w.map(x => { let n = x[1].split(/\s+/).length - debt; const part = []; debt = 0;
    while (j < toks.length && (n > 0 || toks[j][1] === 0)) { part.push(toks[j][0]); n -= toks[j][1]; j++; }
    if (n < 0) debt = -n; return part.join(' ') || ''; });
  return (chunkCache[k] = j === toks.length ? out.filter(Boolean).length ? out : w.map(x => x[1]) : w.map(x => x[1]));
}
function sayWords(who, words, on) {   // озвученная реплика целиком, произнесённые слова ярче
  const key = who + '|' + on + '|' + words.join(' '); if (key === lastSubKey) return; lastSubKey = key;
  plate(who); dlgEl.querySelector('.ln').innerHTML = words.map((w, i) => `<span class="${i < on ? 'on' : ''}">${w}</span>`).join(/^(zh|ja)$/.test(LNG) ? '' : ' ');   // китайский и японский — без пробелов между кусками
}
let camHud = false;
function setCamHud(on) { if (on !== camHud) { camHud = on; clockEl.classList.toggle('dim', on); } }   // на экране камеры время показывает сама камера
let holdN = -1;   // инструменты (__ns.seek): дорисовать кадр и держать его, не пересчитывая каждый кадр (листы кадров — без лишней нагрузки)
function frame(tp) {
  if (holdN === 0 && paused) { lastP = tp; return requestAnimationFrame(frame); }
  if (holdN > 0) holdN--;
  const fs = performance.now(), dt0 = Math.max(0, (tp - lastP) / 1000), dt = Math.min(.1, dt0); if (window.World && World.perf && (mode === 'play' || mode === 'menu')) World.perf(tp - lastP); lastP = tp; if (!paused) clockT += Math.min(dt0, 4);   // долгий кадр (шейдеры, сборка мусора) — звук уже ушёл вперёд; часы догоняют, чтобы субтитры не отставали
  // слабый телефон в «Ретро»: кадры идут медленно (в среднем > 36 мс за 90 кадров) → 2D-холст 720×1280 вместо 1080×1920, один раз за сессию
  if (CS === 1 && !(window.World && World.cine) && inGame() && !paused && dt0 < .25) { perf2S += dt0; if (++perf2N >= 90) { if (perf2S / perf2N > .036) { CS = 2 / 3; applyLand(LAND); } perf2N = perf2S = 0; } }
  if (!paused && PLAYING.has(mode)) { run.sec += Math.min(1, dt0); if ((runSaveT += dt0) > 5) { runSaveT = 0; runSave(); } }   // сколько реально играли (для журнала в конце)
  const T = now();
  let place = null, quiet = false, cam = false; voiceNow = false;
  if (mode === 'play' && !beat) { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); }
  if (mode === 'play' && beat) {
    if (audioQ && !waiting) bT0 = T + pre;   // первый кадр постановки: отсчёт — с него (пока его ждали — чёрная склейка, шейдеры, — время не идёт)
    if (lateQ.length && !paused) lateFlush(T);
    let t = waiting ? 0 : T - bT0;
    if (t >= bDur) {
      const silent = !(beat.say || []).length && !beat.text;
      if (auto || silent || beat.auto) { nextBeat(); return requestAnimationFrame(frame); }
      if (!holding) { holding = true; holdT = now(); dlgEl.classList.add('wait'); }
      else if (now() - holdT > 2.2 && !dlgEl.classList.contains('wait2')) { dlgEl.querySelector('.nh').textContent = tr(matchMedia('(pointer: fine)').matches ? 'Пробел или клик — дальше' : 'Нажми, чтобы продолжить'); dlgEl.classList.add('wait2'); }   // голос кончился, а игрок ждёт — не «пропала озвучка», а пауза до нажатия
      t = bDur;
    }
    const p = Math.max(0, Math.min(1, t / bDur));
    // тишина перед скримером: за 1.5 с до удара фон и музыка уходят почти в ноль (у кадра с явной тишиной — своя)
    if (scareAt != null && !beat.hush) { const h = t > scareAt - 1.5 && t < scareAt; if (h !== hushOn) hush(h, true); }
    if (pre && t >= 0 && beat._preDone !== beatRun) { beat._preDone = beatRun; if (beat.fx === 'glitch') glitchEnd(); blackT = T - .02; }
    trigTick(t);
    place = placeOf(beat.img, beat.fx);
    quiet = hushOn || (scareAt != null && t > scareAt - 4 && t < scareAt + 2);
    if (t < 0) {   // пред-кадр
      preFrame(t, T); quiet = true;
      if (!dlgEl.classList.contains('hide')) { dlgEl.classList.add('hide'); lastSubKey = ''; }
    } else {
      g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
      // кто сейчас говорит и насколько громко: от этого двигается рот персонажа в кадре
      let talk = null;
      if (!waiting) {
        let cur = null; for (const it of sched) if (t >= it.t) cur = it;
        talkV += (voiceLevel() - talkV) * .5;
        if (cur && t < cur.t + cur.d) { if (aloudOf(cur.k)) talk = { who: whoOf(cur.k), v: talkV, vis: visemeAt(cur.k, t - cur.t) }; }
        else if (typed && typed.s && t >= typed.t0 && t < typed.t0 + typed.d - .9 && (typed.who && typed.who !== 'ИТАН' || typed.s.trim().startsWith('—'))) talk = { who: typed.who || beat.who || 'ИТАН', v: .25 + .55 * Math.abs(Math.sin(t * 11)) };
      }
      talkWho = talk ? talk.who : null; talkFx = talk && cur0() ? (TIM[cur0().k] || {}).fx || '' : '';   // для автопроверки кадров: кто сейчас говорит вслух (и не по рации ли)
      // скример: вспышка, рывок, сильная тряска
      const sc = scareAt != null && t >= scareAt, st = sc ? t - scareAt : 0;
      const lg = beat.fx === 'lunge';
      if (sc && st < .05) { if (blackT !== T && T - blackT > .5) buzz(lg ? [250, 40, 350] : [90, 30, 160]); if (!lg) blackT = T; shakeAmp = lg ? (calm() ? 30 : 46) : 22; }
      if (lg && sc && st < .7) shakeAmp = Math.max(shakeAmp, (calm() ? 20 : 34) * (1 - st / .7));
      const fixedCam = beat.fx === 'cctv' || beat.fx === 'static' || (beat.fx === 'glitch' && !!beat.label), lk = lookStep(dt, fixedCam || beat.fx === 'lunge' || !!beat.phone);
      const hd = handCam(T, fixedCam ? 0 : 1), sx = lk.x + hd.x + noise1(T * 14, 7) * shakeAmp * .55, sy = lk.y + hd.y + noise1(T * 14, 8) * shakeAmp * .55, sr = hd.r + noise1(T * 12, 9) * shakeAmp * .3;   // камера наблюдения висит на стене — не дышит
      shakeAmp *= Math.pow(.9, dt * 30);
      if (beat.phone) phone(beat.phone, t);
      else if (beat.img) {
        const cams = beat.cams || [[.5, .5, 1, 1.1]], n = cams.length; let ci, lp;
        if (bCuts && bCuts.length === n) { ci = 0; while (ci < n - 1 && t >= bCuts[ci + 1]) ci++; const a = bCuts[ci], b = ci < n - 1 ? bCuts[ci + 1] : bDur; lp = clamp((t - a) / Math.max(.1, b - a), 0, 1); }
        else { ci = Math.min(n - 1, Math.floor(p * n)); lp = p * n - ci; }
        let c = cams[ci]; const still = beat.fx === 'photo';   // фото не дышит и не двигается: снимок
        if (Array.isArray(c[0]) && window.World && World.projectAt) { const q = World.projectAt(c[4] || beat.img, c[0]);   // план целится в точку мира (лицо), а не в долю экрана
          c = [LAND ? .5 + (q[0] - .5) * (16 / 9) / (9 / 16) : q[0], q[1] + (c[1] || 0), c[2], c[3], c[4]]; }   // в 16:9 shot() пересчитает доли из 9:16 (scrX) — отдаём в долях 9:16; c[1] — сдвиг по вертикали (минус — точка выше центра)
        let z = c[2] + (c[3] - c[2]) * ease(lp); if (sc && beat.fx !== 'lunge') z = Math.max(z, c[3]) * (1.1 + .04 * Math.exp(-st * 6));
        const gl = still || sc ? { cx: c[0], cy: c[1], z } : camGlide(c[4] || beat.img, c[0], c[1], z, T, beatRun, ci);   // тот же план в следующей реплике: камера доезжает, а не прыгает
        const cr = creep && t >= creep.at && t < creep.at + creep.len ? creep.seed : 0;
        if (cr && !creep.hit) { creep.hit = true; if (Math.random() < .35) play('whispers', { gain: .18 }); }   // молча: заметит не каждый — так страшнее
        cam = beat.fx === 'cctv' || beat.fx === 'static' || (beat.fx === 'glitch' && !!beat.label);
        // реакции тела по ходу реплики: beat.react = { ГАС: ['no', 1.2, 3.5] } — кто, что (yes/no/arms) и с какой по какую секунду кадра
        let react = null; if (reactW) for (const who in reactW) { const r = reactW[who]; if (t >= r[1] && t < r[2]) (react = react || {})[who] = r[0]; }
        const so = still ? { cx: c[0], cy: c[1], z, t: (beat.photo || {}).t ?? 2, T: 100, sx: 0, sy: 0 } : { cx: gl.cx, cy: gl.cy, z: gl.z, t: t + shotOff, T, sx, sy, sr, talk, react, creep: cr, scare: sc ? Math.exp(-st * 2.2) : 0 };
        if (cam) so.cctv = 1;
        const im = c[4] || beat.img;   // план может быть другим кадром (склейка на другой ракурс посреди реплики)
        if (still) {   // фото — неподвижный снимок: 3D и эффект старого фото считаются один раз на план и кладутся в кэш, дальше только копия с наездом
          const key = sceneId + '|' + bi + '|' + ci + '|' + W + '|' + LNG + '|' + (window.World ? World.vhs : 0), ccx = LAND ? scrX(c[0]) : c[0];   // язык (надпись на обороте) и плёнка меняются из паузы — тогда пересчёт   // (в 2D эффект с режимами наложения стоил на телефоне сотни мс на кадр — сцена «висела», озвучка уезжала)
          if (!stillCache || stillCache.key !== key) { shot(im, Object.assign({}, so, { z: c[2] })); photoFx(beat.photo || {}, ci, c[2]);
            stillC.width = W; stillC.height = H; stillC.getContext('2d').drawImage(cv, 0, 0); stillCache = { key, z0: c[2], cx: ccx, cy: c[1] }; }
          const k = z / stillCache.z0, sw = W / k, sh = H / k; g.drawImage(stillC, stillCache.cx * W - sw * stillCache.cx, stillCache.cy * H - sh * stillCache.cy, sw, sh, 0, 0, W, H);
        } else if (cam && beat.fx !== 'static') heldShot(im, so, T, im + ci); else shot(im, so);   // камера наблюдения — 12 кадров в секунду
        knockSync(T);
        if (beat.noise) noisePatch(beat.noise, T);
        // кадр-вспышка: лицо на 1–2 кадра поверх, в темноте
        if (subl && t >= subl.at && subl.n < 2) { if (shot(subl.img, { cx: .5, cy: .45, z: 1.12, t: 2.2, T, sx: 0, sy: 0 })) { subl.n++; sublScene = sceneId; } else subl.n = 2; }
        if (ci !== beat._ci) { beat._ci = ci; if (ci > 0) blackT = T - .02; }
        if (beat.fx === 'static' && t > 1.2) { grain(.55); g.fillStyle = `rgba(255,255,255,${(calm() ? .02 : .04) + .04 * Math.sin(t * 40)})`; g.fillRect(0, 0, W, H); }
        // дождь перед объективом — только когда мы сами снаружи (внутри холла капли — на стекле, это рисует 3D)
        if (beat.fx === 'rain' && place && place.out) { g.strokeStyle = 'rgba(200,215,230,.28)'; g.lineWidth = 2; g.beginPath(); for (const d of drops) { const y = (d.y + T * d.v) % (H + 100) - 100, x = (d.x + y * .12) % W; g.moveTo(x, y); g.lineTo(x + d.l * .12, y + d.l); } g.stroke(); grain(.08); }
        if (beat.fx === 'lamp' && (T * 3.1) % 9 < .25) { g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(0, 0, W, H); }
        if (beat.fx === 'flicker' && (T % 4) < .5 && !calm()) { g.fillStyle = `rgba(255,255,255,${.04 * Math.abs(Math.sin(T * 31))})`; g.fillRect(0, 0, W, H); }
        if (beat.fx === 'dawn') { g.save(); g.globalCompositeOperation = 'soft-light'; g.fillStyle = 'rgba(255,160,80,.75)'; g.fillRect(0, 0, W, H); g.globalCompositeOperation = 'screen'; g.fillStyle = `rgba(70,35,10,${.15 + .15 * p})`; g.fillRect(0, 0, W, H); g.restore(); grain(.06); }
        // стробоскоп: видно лишь урывками — 60–120 мс света и почти полсекунды темноты; мозг дорисовывает сам
        if (beat.fx === 'strobe' && t > .5 && ((t * 1000) % 470) > 95 + 40 * Math.sin(t * 7)) { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); }
        if (beat.fx === 'dark') { g.fillStyle = `rgba(0,0,0,${darkHold ? .85 : p * .85})`; g.fillRect(0, 0, W, H); }

        if (beat.light) { g.fillStyle = `rgba(0,0,0,${.35 * (1 - Math.min(1, t / 6))})`; g.fillRect(0, 0, W, H); }
        if (beat.fin) { g.fillStyle = `rgba(0,0,0,${Math.min(1, .1 + .22 * Math.min(1, t / 2.5) + .7 * Math.max(0, (t - bDur + 1.4) / 1.4))})`; g.fillRect(0, 0, W, H); }   // финал: чуть темнеет (низ под название затемняет .fin), в конце — в черноту
        if (beat.floors) { const f = Math.min(7, 1 + Math.floor(t / .6)); g.save(); g.translate(W / 2, 0); g.scale(US, US); g.fillStyle = 'rgba(0,0,0,.6)'; rr(-90, 220, 180, 150, 20); g.fillStyle = '#f33'; g.font = '800 110px Inter, Arial'; g.textAlign = 'center'; g.fillText(f, 0, 245); g.restore(); }
        if (!beat.fx || beat.fx === 'rain') grain(.07);
        if (sc && st < .6) grain(.5 * (1 - st / .6));
        // бросок: двоится от рывка, потом обрыв в черноту (крик ещё звучит)
        if (lg && sc && st < .8 && !calm()) { g.save(); g.globalAlpha = .35 * (1 - st / .8); g.globalCompositeOperation = 'lighter'; g.drawImage(cv, 16 + Math.random() * 20, 0); g.drawImage(cv, -16 - Math.random() * 20, 6); g.restore(); }
        if (lg && sc && st > .8) { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); grain(.2); }
        // кадр-вспышка: на миг — оскал вплотную, в красном, и негатив; мозг не успевает разглядеть, но запоминает
        if (lg && sc && !calm() && st > .5 && st < .58) { shot('monster_close', { cx: .5, cy: .45, z: 1.35, t: 2.2, T, sx: 0, sy: 0, scare: 1 }); g.save(); g.globalCompositeOperation = 'multiply'; g.fillStyle = '#ff2a20'; g.fillRect(0, 0, W, H); g.restore(); }
        if (lg && sc && !calm() && ((st > .09 && st < .13) || (st > .3 && st < .33))) { g.save(); g.globalCompositeOperation = 'difference'; g.fillStyle = '#fff'; g.fillRect(0, 0, W, H); g.restore(); }
        vignette();
        // смерти: snow — сигнал камеры рвётся помехами ([[с, по], …]); black — с этой секунды обрыв в черноту (звук ещё идёт в темноте)
        if (beat.snow) for (const [a, b] of beat.snow) if (t >= a && t < b) { grain(.95); tear(2); g.fillStyle = `rgba(225,232,226,${.08 + .1 * Math.random()})`; g.fillRect(0, 0, W, H); }
        if (beat.hud && (beat.hud !== hudRef || (!beat.fin && t < .1))) { hudRef = beat.hud; hudT0 = now() - t; }   // финал берёт тот же hud — часы записи идут дальше, а не с нуля
        if (cam) cctvHud(beat.label, T, beat.hud && { tag: beat.hud.tag ? tr(beat.hud.tag) : 'REC', date: beat.hud.date, clock: beat.hud.at + ':' + String(Math.floor(now() - hudT0) % 60).padStart(2, '0') });   // hud — своя дата и время записи (другая ночь; tag — «▶ ЗАПИСЬ», когда смотрят запись)
        if (beat.black != null && t >= beat.black) { const js = t - beat.black;
          if (sceneId.startsWith('over_') && js < JUMP_D) {   // скример смерти: лицо Жильца с открытой пастью бросается в объектив (зум), тряска, красный; потом чернота
            if (js < .05) { shakeAmp = Math.max(shakeAmp, 40); if (!jumpBuzz) { jumpBuzz = 1; buzz([200, 40, 300]); } }
            shot('death_scream', { cx: .5, cy: .5, z: 1.05 + .65 * Math.min(1, js / .4), t: 2.2 + js, T, sx: 0, sy: 0, scare: 1 - js / JUMP_D });
            if (!calm()) { g.save(); g.globalCompositeOperation = 'multiply'; g.fillStyle = `rgb(255,${60 + 150 * js / JUMP_D | 0},40)`; g.fillRect(0, 0, W, H); g.restore();
              if (js < .05) { g.save(); g.globalCompositeOperation = 'difference'; g.fillStyle = '#fff'; g.fillRect(0, 0, W, H); g.restore(); } }
            grain(.3);
          } else if (sceneId.startsWith('over_') && goreKind(sceneId) && js < JUMP_D + GORE_D) {   // финал: как оно убивает — крупно, 2,6 с
            const gt = js - JUMP_D, gk = goreKind(sceneId);
            const cl = gk !== 'drag' && gt > .55 && gt < 2.15;   // раскадровка: общий план → крупно рана (когти, рывок) → общий (ест)
            shot('gore@' + (GORE_BASE[sceneId] || beat.img) + ':' + gk + (cl ? '|c' : ''), { cx: .5, cy: .5, z: 1 + .04 * gt, t: gt, T, sx: 0, sy: 0 });
            if (gt < .12 && !calm()) { g.save(); g.globalCompositeOperation = 'multiply'; g.fillStyle = '#ff3030'; g.fillRect(0, 0, W, H); g.restore(); }
            if (/^(face|throat|eyes|chest|hang)/.test(gk) && gt > .55) splatBlood(g, W, H, Math.min(1, (gt - .55) * 3), sceneId.length * 7 + gk.length);
            if (gt > GORE_D - .35) { g.fillStyle = `rgba(0,0,0,${(gt - GORE_D + .35) / .35})`; g.fillRect(0, 0, W, H); }
            grain(.25);
          } else { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); } }
        else jumpBuzz = 0;
      }
      // реплики: табличка с именем того, кто говорит; слова загораются по мере звучания
      let shown = false;
      if (!waiting && !(beat.black != null && t >= beat.black)) {   // после обрыва в черноту субтитров нет
        if (typed && t >= typed.t0 && typed.s) {
          const k = Math.floor(Math.min(1, (t - typed.t0) / (typed.d - .9)) * typed.s.length);
          say(typed.who || beat.who || 'ИТАН', typed.s, k); shown = true;
          if (k < typed.s.length && Math.random() < .25) play('tick', { gain: .25 });
        } else {
          let cur = null; for (const it of sched) if (t >= it.t) cur = it;
          if (cur) {
            const tm = TIM[cur.k] || {}, w = tm.w || [], on = w.filter(x => t - cur.t >= x[0] - .02).length;
            voiceNow = t < cur.t + cur.d + .1; sayWords(whoOf(cur.k), chunks(cur.k, w), voiceNow ? on : w.length); shown = true;
          }
        }
      }
      if (!shown && !dlgEl.classList.contains('hide')) { dlgEl.classList.add('hide'); lastSubKey = ''; }
      if (beat.heart && t > hb) { play('heart', { gain: .8 }); hb = t + Math.max(.55, .9 - t * .02); }
    }
    if (audioQ && !waiting) { audioQ = 0; const d = (performance.now() - fs) / 1000; if (d > .05) bT0 += Math.min(d, 4); beatAudio(); }   // первый кадр нарисован — звук с этого момента; долгую отрисовку (холодные шейдеры) отсчёт не считает
    duck(voiceNow);
  } else if (mode === 'cams' && camView) {
    const t = T - camView.t0, c = camView.c; g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    const jt = camView.jump ? t - 1.25 : -1, sx = noise1(T * 14, 7) * shakeAmp * .55, sy = noise1(T * 14, 8) * shakeAmp * .55; shakeAmp *= Math.pow(.9, dt * 30);
    if (jt >= 0 && jt < .75) shot(c.jump, { cx: .5, cy: .5, z: 1, t: jt, T, sx, sy, cctv: 1, scare: Math.exp(-jt * 2.2) });
    else heldShot(c.img, { cx: c.c[0], cy: c.c[1], z: c.c[2] + t * .04, t, T, sx, sy, cctv: 1 }, T, 'cam' + c.id);
    vignette(); cctvHud(c.id + '  ' + c.name, T); if (jt >= .75) { grain(.9); if (!calm()) { g.fillStyle = 'rgba(255,255,255,.08)'; g.fillRect(0, 0, W, H); } }
    place = placeOf(c.img); cam = true;
  } else if (mode === 'shift' && shf) {
    const c = shf.cam, st = T - shf.camT, img = c === '13' ? null : 'cam' + (c === 'm2' ? '_m2' : c);
    if (!shf.seg.music && curMusicName === 'drone' && (BUF.shift || rawSnd.shift)) music('shift');   // подложка смены докачалась позже старта (декодируется в music())
    g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    if (img) heldShot(img, { cx: .5, cy: .5, z: 1, t: T, T, sx: 0, sy: 0, cctv: 1 }, T, 'sh' + c);
    for (const a of shf.act) if (a.def.cam === c && (a.def.box || a.def.at)) { const k = a.look || 0;   // настоящие камеры показывают его помехами
      if (!a.def.at) { staticBox(a.def.box, T, k); continue; }
      const n = stareN(k), fb = figBox(stareSpot(a.def, k)), jump = n > 0 && k - STARE_T[n] < .18;   // фигура в 3D: чем ближе подошёл, тем реже рябь и виднее он сам
      if (fb) figGlitch(fb, T, n, jump);
      if (jump) { grain(.95); if (a.jumpN !== n) { a.jumpN = n; tear(1); play('static', { gain: .55 + .12 * n }); } } }   // рывок — вспышка ряби на весь кадр и треск
    if (st < .22) grain(.95);   // переключение камеры
    vignette(); cctvHud(camLabel(c), T);
    const v = shf.vq.find(v => T >= v.t0 && (v.hold || T < v.t0 + (v.show || v.d) + .35));   // hold — Вера ждёт «Продолжить» (обучение)
    if (v) { const tm = TIM[v.k];
      if (tm) { const w = tm.w || [], on = w.filter(x => T - v.t0 >= x[0] - .02).length; sayWords(whoOf(v.k), chunks(v.k, w), on); voiceNow = T < v.t0 + v.d; }
      else { const s = tr((S.shift.lines || {})[v.k] || ''); say((S.shift.who || {})[v.k] || 'ИТАН', s, Math.floor(Math.min(1, (T - v.t0) / Math.max(.5, v.d - .8)) * s.length)); } }
    else if (!dlgEl.classList.contains('hide')) { dlgEl.classList.add('hide'); lastSubKey = ''; }
    place = img ? placeOf(img) : null; cam = true;
  } else if (mode === 'mg' && mgView) {
    const t = T - mgView.t0, k = Math.min(1, t / (mgView.dur || 3)); g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    shot(mgView.img, { cx: mgView.c[0], cy: mgView.c[1], z: mgView.c[2] + (mgView.c[3] - mgView.c[2]) * k * k, t: mgView.tAt != null ? mgView.tAt : mgView.st ? mgView.st + t : t, T, sx: noise1(T * 14, 7) * shakeAmp * .55, sy: noise1(T * 14, 8) * shakeAmp * .55, sr: mgView.label ? 0 : noise1(T * 12, 9) * shakeAmp * .3, cctv: mgView.label ? 1 : undefined, torch: mgView.torch });
    if (mgView.fog) { g.imageSmoothingEnabled = true; g.drawImage(mgView.fog, 0, 0, W, H); }
    // перемотка: дрожит и рвётся строками, пока тянут ползунок
    if (mgView.rw > .02) { const n = 2 + Math.floor(mgView.rw * 5); for (let i = 0; i < n; i++) { const y = Math.random() * H, h = 6 + Math.random() * 30 * mgView.rw; g.drawImage(cv, 0, y, W, h, (Math.random() - .5) * 90 * mgView.rw, y, W, h); g.fillStyle = `rgba(255,255,255,${.06 + .12 * Math.random() * mgView.rw})`; g.fillRect(0, y, W, 2 + Math.random() * 4); } mgView.rw *= .88; }
    if (mgView.run) { const L = mgView.run === 'L', gr = g.createLinearGradient(L ? 0 : W, 0, W / 2, 0); gr.addColorStop(0, 'rgba(0,0,0,.97)'); gr.addColorStop(.7, 'rgba(0,0,0,.75)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, W, H); }
    shakeAmp *= Math.pow(.9, dt * 30); vignette(); if (mgView.label) { cctvHud(mgView.label, T, mgView.rwHud); if (t > .3) grain(mgView.tAt != null ? .2 : .25 + k * .5); }
    place = placeOf(mgView.img); quiet = true; cam = !!mgView.label;
  } else if (mode === 'menu') {
    // титул: живой кадр, медленный наезд (20 с), сверху темнее — там логотип, снизу — меню
    if (curMusicName !== 'theme' && BUF.theme) music('theme');   // тема титула — как только раскодирована
    const tt = T - titleT0, z = 1 + .06 * ease(Math.min(1, tt / 20));
    shot(TITLE_SHOT, { cx: .5, cy: .47, z, t: tt + 3, T, sx: 0, sy: 0 }); knockSync(T);
    let gr = g.createLinearGradient(0, 0, 0, H * .5); gr.addColorStop(0, 'rgba(0,0,0,.88)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, W, H * .5);
    gr = g.createLinearGradient(0, H * .55, 0, H); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,.9)'); g.fillStyle = gr; g.fillRect(0, H * .55, W, H * .45);
    grain(.09); vignette();
    if (tt < 1.4) { g.fillStyle = `rgba(0,0,0,${1 - tt / 1.4})`; g.fillRect(0, 0, W, H); }
    place = { loc: 'lobby', rain: 'glass' };
  } else if ((mode === 'explore' || mode === 'talk') && act) {
    // обход: кадр места почти без затемнения, точки на предметах; разговор — собеседник в кадре, темы внизу
    // разговор в горизонтали: темы слева, кадр смещён — собеседник справа, лицо не под списком
    const c = act.cams[0], tt = now() - act.t0, k = 1 - Math.pow(1 - Math.min(1, tt / 8), 2), side = mode === 'talk' && LAND ? .14 : 0;
    shot(act.th.img, { cx: c[0] - side, cy: c[1], z: c[2] + (c[3] - c[2]) * k, t: tt + (act.th.t || 0), T, sx: 0, sy: 0 });
    if (mode === 'talk' && LAND) { const gr = g.createLinearGradient(0, 0, W * .55, 0); gr.addColorStop(0, 'rgba(0,0,0,.85)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, W * .55, H); }
    else if (mode === 'talk') { const gr = g.createLinearGradient(0, H * .45, 0, H); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,.85)'); g.fillStyle = gr; g.fillRect(0, H * .45, W, H * .55); }
    grain(.08); vignette(); place = placeOf(act.th.img); actSpots();
  } else if (mode !== 'play') {
    // фон выбора/концовки: последний кадр затемнён, медленно дышит
    if (mode === 'studio' || mode === 'load') { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); }   // заставка и загрузка — на чёрном
    else if (mode === 'end' && overBg) {   // экран смерти: сначала чернота, потом медленно проступает застывший последний кадр (то, что убило)
      const k = clamp((T - overBg.t0 - .9) / 2.2, 0, 1);
      if (k > 0) { shot(lastDrawn || TITLE_SHOT, { cx: .5, cy: .5, z: 1.02 + .02 * k, t: overBg.st, T: overBg.st + 100, sx: 0, sy: 0 }); g.fillStyle = `rgba(0,0,0,${1 - .4 * ease(k)})`; g.fillRect(0, 0, W, H); grain(.1); vignette(); place = placeOf(lastDrawn); }
      else { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); }
    }
    else { shot(lastDrawn || TITLE_SHOT, { cx: .5, cy: .5, z: 1.02 + .03 * Math.sin(T * .3), t: T, T }); if (mode === 'choice') knockSync(T); g.fillStyle = `rgba(0,0,0,${mode === 'choice' ? .45 : .62})`; g.fillRect(0, 0, W, H); grain(.1); vignette(); place = placeOf(lastDrawn); }
  }
  setCamHud(cam && mode === 'play');
  if (T - flashT < .2 && !calm()) { g.fillStyle = `rgba(255,255,255,${(1 - (T - flashT) / .2) * .7})`; g.fillRect(0, 0, W, H); }
  if (T - blackT < .09) { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); }   // склейка/скример: пара чёрных кадров, без белой вспышки
  // звук места: фон, далёкие звуки, лампа
  if (AC && bed) {
    const lv = inGame() ? (voiceNow ? .45 : 1) : mode === 'menu' ? (gateDone ? .9 : 0) : mode === 'end' ? .5 : 0;   // под голос фон уходит вниз, чтобы дождь не глушил речь
    bedSet(place && place.loc, place && place.rain, lv);
    if (lv > 0) { ambTick(T, mode === 'menu' ? 'title' : bedWant.loc, quiet || voiceNow || mode === 'mg'); tubeTick(T); }
  }
  if (ambOn && (++ambN & 3) === 0) { const ah = ambC.height, sh = Math.min(H, W * ah / ambC.width); ambG.drawImage(cv, 0, (H - sh) / 2, W, sh, 0, 0, ambC.width, ah); }
  requestAnimationFrame(frame);
}
function trigTick(t) { for (const e of trig) if (!e.done && t >= e.at) { e.done = true; try { e.fn(); } catch (er) { console.error(er); } } }

/* ---------- старт ---------- */
// ориентация кадра на лету: холст движка, буферы кадра, раскладка интерфейса
function applyLand(l) {
  LAND = !!l; W = Math.round((LAND ? 1920 : 1080) * CS); H = Math.round((LAND ? 1080 : 1920) * CS); US = H / 1920; if (act) act.laid = false;   // точки обхода — разложить подписи заново
  for (const c of [cv, heldC, frozenC]) { c.width = W; c.height = H; }
  heldT = -9; drops = drops.map(() => ({ x: Math.random() * W, y: Math.random() * H, v: 1800 + Math.random() * 1400, l: 40 + Math.random() * 70 }));
  document.body.classList.toggle('land', LAND);
  if (window.World && World.setLand) World.setLand(LAND);
  ambFit();
}
$('bMute').onclick = () => setMute(!LS.get('mute', false));
$('bMute').classList.toggle('muted', LS.get('mute', false));
$('bMenu').onclick = e => { e.stopPropagation(); pauseMenu(); };
$('bFull').onclick = e => { e.stopPropagation(); goFull(); }; fullBtn();
$('bRules').onclick = e => { e.stopPropagation(); showRules(); };
$('bAuto').onclick = e => { e.stopPropagation(); auto = !auto; LS.set('auto', auto); $('bAuto').classList.toggle('on', auto); };
$('bAuto').classList.toggle('on', auto);
$('stage').addEventListener('pointerdown', e => { if (e.target.closest('button, #choice, #mg, #ui, #hud, #note, #pz, #qa, #qaP')) return; tap(); });
// оглядеться: на ПК взгляд чуть ведёт мышь, на телефоне — наклон (где браузер даёт гироскоп без запроса). Камеры наблюдения и бросок — неподвижны
const lookT = { x: 0, y: 0 }, look = { x: 0, y: 0 }; let tilt0 = null;
const lookOn = () => LS.get('look', true);
// камера «с рук»: гладкий шум вместо случайного дрожания. Тряска при испуге — частая (14 Гц), но гладкая и одинаковая при 30 и 144 кадрах;
// дыхание оператора — медленный дрейф и лёгкий крен (в единицах sx: ×0,00012 рад; крен ×0,0004 рад), не синусоида с заметным периодом
const hsh = n => { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };
function noise1(x, seed = 0) { const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f), o = seed * 57.31; return (hsh(i + o) * (1 - u) + hsh(i + 1 + o) * u) * 2 - 1; }
function handCam(T, k = 1) {
  if (!k) return { x: 0, y: 0, r: 0 };
  return { x: (noise1(T * .33, 1) * 10 + noise1(T * 1.07, 2) * 4 + noise1(T * 3.1, 3) * 1.4) * k, y: (noise1(T * .29, 4) * 9 + noise1(T * .93, 5) * 3.6 + noise1(T * 2.8, 6) * 1.2) * k, r: (noise1(T * .21, 11) * 5 + noise1(T * .8, 12) * 2) * k };
}
// тот же план в следующей реплике, но наезд или точка чуть другие (в сценарии 22 таких места): камера плавно доезжает за 0,85 с, а не прыгает.
// Большая разница (наезд в 1,2 раза и больше, другая точка) — это задуманная склейка на крупнее, её не трогаем; склейки внутри реплики (по словам) — тоже
let camLast = null, camFrom = null;
function camGlide(im, cx, cy, z, T, run, ci) {
  if (camLast && camLast.run !== run) { const dz = Math.abs(z / camLast.z - 1), dc = Math.hypot(cx - camLast.cx, cy - camLast.cy);
    camFrom = camLast.im === im && (dz > .004 || dc > .003) && dz < .2 && dc < .09 ? { cx: camLast.cx, cy: camLast.cy, z: camLast.z, t0: T } : null; }
  else if (camLast && camLast.ci !== ci) camFrom = null;
  let o = { cx, cy, z };
  if (camFrom) { const k = (T - camFrom.t0) / .85; if (k >= 1 || k < 0) camFrom = null; else { const e = k * k * (3 - 2 * k); o = { cx: camFrom.cx + (cx - camFrom.cx) * e, cy: camFrom.cy + (cy - camFrom.cy) * e, z: camFrom.z + (z - camFrom.z) * e }; } }
  camLast = { im, cx: o.cx, cy: o.cy, z: o.z, run, ci };
  return o;
}
function lookStep(dt, fixed) {
  const tx = fixed || !lookOn() ? 0 : lookT.x, ty = fixed || !lookOn() ? 0 : lookT.y, k = Math.min(1, dt * 2.2);
  look.x += (tx - look.x) * k; look.y += (ty - look.y) * k;
  return { x: look.x * (LAND ? 520 : 300), y: look.y * (LAND ? 260 : 330) };
}
addEventListener('pointermove', e => { if (e.pointerType !== 'mouse') return; const r = $('stage').getBoundingClientRect();
  lookT.x = clamp(((e.clientX - r.left) / r.width - .5) * 2, -1, 1); lookT.y = clamp(((e.clientY - r.top) / r.height - .5) * 2, -1, 1); });
addEventListener('deviceorientation', e => { if (FINE || e.gamma == null) return; const a = LAND ? e.beta : e.gamma, b = LAND ? e.gamma : e.beta;
  if (!tilt0) tilt0 = { a, b }; tilt0.a += (a - tilt0.a) * .004; tilt0.b += (b - tilt0.b) * .004;   // «ноль» медленно подстраивается под то, как держат телефон
  lookT.x = clamp((a - tilt0.a) / 18, -1, 1); lookT.y = clamp((b - tilt0.b) / 22, -1, 1) * (LAND ? -1 : 1); });
document.addEventListener('keydown', e => {
  // горячие клавиши ПК: F — во весь экран, V — озвучка, M — звук, O — повернуть экран
  if (e.key === 'f' || e.key === 'F' || e.key === 'а' || e.key === 'А') { const d = document; if (DESK) DESK.full(); else if (d.fullscreenElement) d.exitFullscreen().catch(() => {}); else goFull(); }
  if (e.key === 'v' || e.key === 'V' || e.key === 'м' || e.key === 'М') setVoice(!voiceOn());
  if (e.key === 'm' || e.key === 'M' || e.key === 'ь' || e.key === 'Ь') setMute(!LS.get('mute', false));
  if (e.key === 'o' || e.key === 'O' || e.key === 'щ' || e.key === 'Щ') { LS.set('land3', !LAND); applyLand(!LAND); if (uiEl.querySelector('.sets')) settings(); }
  if (mode === 'menu' && !gateDone && (e.key === ' ' || e.key === 'Enter')) return openGate();
  if (mode === 'end' && $('oRetry')) { if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); return $('oRetry').click(); } if (e.key === 'Escape') { e.preventDefault(); return $('oMenu').click(); } }   // экран смерти на ПК
  if (e.key === ' ' || e.key === 'Enter') tap(); if (mode === 'choice' && /^[1-4]$/.test(e.key)) { const b = choiceEl.querySelectorAll('button')[+e.key - 1]; if (b) b.click(); }
  if (mode === 'talk' && /^[1-9]$/.test(e.key)) { const b = actEl.querySelectorAll('button')[+e.key - 1]; if (b) b.click(); } if (e.key === 'Escape') { const sx = (pzEl.classList.contains('hide') ? uiEl : pzEl).querySelector('.sx'); if (sx) sx.click(); else if (inGame()) pauseMenu(); else if (mode === 'end') menu(); } });
// ПК: размытый отсвет кадра по бокам экрана (виден только при широком окне и мыши)
const ambC = $('amb'), ambG = ambC.getContext('2d'); let ambOn = false, ambN = 0;
function ambFit() { ambOn = getComputedStyle(ambC).display !== 'none'; if (ambOn) ambC.height = Math.max(8, Math.round(64 * innerHeight / innerWidth)); }
addEventListener('resize', ambFit); ambFit();
applyLand(LAND);
requestAnimationFrame(frame);
window.addEventListener('error', e => { if (mode === 'menu' && !uiEl.innerHTML) uiEl.innerHTML = `<div class="screen"><p>${tr('Ошибка: ')}${String(e.message || e).slice(0, 120)}</p></div>`; });
// холодный старт: заставка студии 1.4 с (тап — пропустить) идёт, пока грузится мир; потом — карточка «наушники» с полосой загрузки, если ещё грузится
let loaded = false, splashDone = false, cardT = 0;
function studio() {
  if (RESUME) { splashDone = true; return afterSplash(); }   // сменили язык/графику — заставку не показываем
  mode = 'studio';
  uiEl.innerHTML = `<div class="studio"><div class="stw"><div class="logo"><i></i><b>YOMPIX</b><span>GAMES</span></div></div></div>`;
  const go = () => { if (splashDone) return; splashDone = true; uiEl.onclick = null; afterSplash(); };
  uiEl.onclick = go; setTimeout(go, 1400);
}
function afterSplash() {
  if (loaded) return RESUME ? resumeAfterReload() : menu();
  mode = 'load'; cardT = now();
  uiEl.innerHTML = `<div class="screen boot"><div class="card">${ICON.phones}<span>${tr('18+ · жестокость и кровь · лучше в наушниках · в игре есть вспышки света и резкие звуки')}</span></div><div id="loadbar"><i style="width:${loadP * 100}%"></i></div></div>`;
}
studio();
// остальные места догреваются в фоне по одному (World.warmMore: шейдеры собирает драйвер в своих потоках, кадр не рисуется): в меню и на паузе — подряд и с тенями, в игре — реже и без лишних кадров
function warmBg() { const idle = mode === 'menu' || userPause; let p = null; try { p = window.World && World.warmMore && World.warmMore(idle); } catch (e) { console.warn(e); } if (p) p.then(() => setTimeout(warmBg, idle ? 40 : 700)); }
load(p => { loadP = p; const b = document.querySelector('#loadbar i'); if (b) b.style.width = p * 100 + '%'; }).catch(() => {}).then(() => {
  initAudio(); loaded = true; setTimeout(warmBg, 1600);
  if (splashDone) setTimeout(() => RESUME ? resumeAfterReload() : menu(), RESUME ? 0 : Math.max(0, 1200 - (now() - cardT) * 1000));   // карточку не мелькаем — хотя бы секунда
});
window.__ns = { back: () => menu(), startScene, get cam() { return camLast; }, get flags() { return flags; }, setFlags: f => { for (const k in flags) delete flags[k]; Object.assign(flags, f); LS.set('flags', flags); }, get music() { return curMusicName; }, get fin() { return !!(mode === "play" && beat && beat.fin); }, shSpeed: v => (shSpd = v), get shf() { return shf; }, get shProps() { return mode === 'shift' && shf ? shiftProps() : null; }, get nowT() { return now(); }, get shk() { return shk; }, shiftCam: c => shiftCam(c), shiftLog: () => shiftLog(), shiftPanel: () => shiftPanel(), shiftBook: () => shiftBook(), get mode() { return mode; }, get scene() { return sceneId; }, skipBeat, showSms, get dbg() { return [clockT.toFixed(2), bT0.toFixed(2), bDur, bi, holding, waiting, paused, pre].join(' '); }, get audio() { return AC ? AC.state : null; },
  get voiceMiss() { return voiceMiss; }, set voiceMiss(v) { voiceMiss = v; }, get voiceLate() { return voiceLate; },
  // для режима проверки и инструментов: начать сцену как из меню, номер и секунда кадра, пауза, реплики, перемотка кадра на секунду (картинка, без звука)
  begin, get bi() { return bi; }, get bt() { return mode === 'play' && beat && !waiting ? Math.max(0, now() - bT0) : 0; }, get tim() { return TIM; }, pause: on => { if (!on) holdN = -1; setPause(!!on); }, get userPause() { return userPause; },
  get bdur() { return mode === 'play' && beat ? bDur : 0; }, get beatImg() { return mode === 'play' && beat && !beat.phone ? beat.img || null : null; }, get talkWho() { return mode === 'play' ? talkWho : null; }, get talkFx() { return talkFx; },
  seek: t => { if (mode !== 'play' || !beat) return false; setPause(true); audioQ = 0; if (waiting) { waiting = false; tapEl.classList.add('hide'); } holding = false; bT0 = now() - t;
    // перемотка — не склейка: без чёрных кадров склейки (на паузе часы стоят — чёрный кадр остался бы навсегда)
    const n = (beat.cams || [0]).length; let ci = Math.min(n - 1, Math.floor(Math.max(0, Math.min(1, t / bDur)) * n)); if (bCuts && bCuts.length === n) { ci = 0; while (ci < n - 1 && t >= bCuts[ci + 1]) ci++; }
    beat._ci = ci; if (pre && t >= 0 && beat._preDone !== beatRun) { beat._preDone = beatRun; if (beat.fx === 'glitch') glitchEnd(); } blackT = -9; holdN = 3; settleQ = true; heldT = -9; return true; },   // heldT: кадр камеры наблюдения на паузе иначе не перерисовывался (часы стоят)
  get held() { return holdN === 0; }, get canvas() { return [W, H, CS]; }, get bufN() { return Object.keys(BUF).length; }, get voiceN() { return Object.keys(BUF).filter(isVoice).length; },
  get vols() { const g = n => n ? +n.gain.value.toFixed(3) : null; return { out: g(outG), mus: g(uMus), sfx: g(uSfx), sfxRev: g(sfxRev), sfxLow: g(sfxLow), amb: g(uAmb), bedLow: g(bedLow), voice: g(voiceOut), voiceRev: g(voiceRev), direct: g(directG), bed: g(bedG) }; } };
// режим проверки для владельца (qa.js): ?qa=1 — включить (запоминается), ?qa=0 — выключить; в демо itch.io не включается
{ const q = /[?&]qa=([01])/.exec(location.search); if (q) LS.set('qa', q[1] === '1'); if (LS.get('qa', false) && !DEMO) { const sc = document.createElement('script'); sc.src = 'qa.js?v=2'; document.head.appendChild(sc); } }
})();
