/* «Ночная смена» — движок интерактивного кино: кадры с движением камеры и эффектами, голос, субтитры, выборы, мини-игры. */
(() => {
'use strict';
const S = window.STORY, $ = id => document.getElementById(id);
const cv = $('cv'), g = cv.getContext('2d'), W = 1080, H = 1920;
const dlgEl = $('dlg'), tapEl = $('tap'), chapEl = $('chap'), clockEl = $('clock'), noteEl = $('note'), ruleEl = $('rule'), choiceEl = $('choice'), uiEl = $('ui'), mgEl = $('mg');
const LS = { get(k, d) { try { const v = localStorage.getItem('ns1_' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
             set(k, v) { try { localStorage.setItem('ns1_' + k, JSON.stringify(v)); } catch {} } };

/* ---------- загрузка ---------- */
const IMG = {}, BUF = {}; let TIM = {};
const IMGS = ['f01', 'f02', 'f03', 'f04', 'f05', 'f06', 'f07', 'f08', 'f09', 'f10', 'f11', 'f12'];
const SNDS = ['drone', 'tension', 'boom', 'scare', 'knock', 'ding', 'heart', 'rain', 'static', 'buzz', 'ring', 'tick', 'click', 'door', 'steps',
  'ch_guard', 'ch_katya', 'ch_whisper', 'ch_monster', ...Array.from({ length: 17 }, (_, i) => 'l' + String(i + 1).padStart(2, '0'))];
let AC = null, master, musicG, voiceG, sfxG, rawSnd = {};
// XHR вместо fetch: так файлы читаются и внутри Android-приложения (file://)
function get(url, type) { return new Promise((ok, no) => { const x = new XMLHttpRequest(); x.open('GET', url); x.responseType = type; x.onload = () => (x.status === 200 || x.status === 0) && x.response ? ok(x.response) : no(); x.onerror = no; x.send(); }); }
async function load(onp) {
  let done = 0; const total = IMGS.length + SNDS.length + 1, tick = () => onp(++done / total);
  const ims = IMGS.map(k => new Promise(r => { const im = new Image(); im.onload = im.onerror = () => { tick(); r(); }; im.src = 'img/' + k + '.webp'; IMG[k] = im; }));
  const sn = SNDS.map(k => get('snd/' + k + '.mp3', 'arraybuffer').then(b => { rawSnd[k] = b; tick(); }).catch(tick));
  const tm = get('timing.json', 'json').then(j => { TIM = j || {}; tick(); }).catch(tick);
  await Promise.all([...ims, ...sn, tm]);
}
let decoding = null;
function initAudio() {   // контекст создаём заранее (он «спит» до нажатия), звуки раскодируем в фоне
  if (decoding) return decoding;
  const Ctx = window.AudioContext || window.webkitAudioContext; if (!Ctx) return (decoding = Promise.resolve());
  AC = new Ctx();
  master = AC.createGain(); master.connect(AC.destination); master.gain.value = LS.get('mute', false) ? 0 : 1;
  musicG = AC.createGain(); musicG.gain.value = .55; musicG.connect(master);
  voiceG = AC.createGain(); voiceG.gain.value = 1; voiceG.connect(master);
  sfxG = AC.createGain(); sfxG.gain.value = .8; sfxG.connect(master);
  return (decoding = Promise.all(Object.entries(rawSnd).map(([k, b]) => new Promise(r => {
    try { const pr = AC.decodeAudioData(b.slice(0), d => { BUF[k] = d; r(); }, () => r()); if (pr && pr.catch) pr.catch(() => r()); } catch { r(); }
  }))));
}
function unlockAudio() {   // только внутри нажатия: иначе телефон (особенно iPhone) не даст включить звук
  if (!AC) return;
  try { AC.resume(); const b = AC.createBuffer(1, 1, 22050), s = AC.createBufferSource(); s.buffer = b; s.connect(AC.destination); s.start(0); } catch {}
}
const playing = new Set();
function play(name, { when = 0, bus = 'sfx', loop = false, gain = 1, off = 0 } = {}) {
  if (!AC || !BUF[name]) return null;
  const s = AC.createBufferSource(), gn = AC.createGain(); s.buffer = BUF[name]; s.loop = loop; gn.gain.value = gain;
  s.connect(gn); gn.connect(bus === 'voice' ? voiceG : bus === 'music' ? musicG : sfxG);
  s.start(AC.currentTime + Math.max(0, when), off); const o = { s, gn, name }; playing.add(o); s.onended = () => playing.delete(o); return o;
}
function stopAll(keepMusic = true) { for (const o of [...playing]) if (!(keepMusic && o.music)) { try { o.s.stop(); } catch {} playing.delete(o); } }
let curMusic = null, curMusicName = '';
function music(name) {
  if (!AC || name === curMusicName) return; curMusicName = name;
  const old = curMusic; if (old) { old.gn.gain.setTargetAtTime(0, AC.currentTime, .6); setTimeout(() => { try { old.s.stop(); } catch {} }, 2500); }
  curMusic = name ? play(name, { bus: 'music', loop: true, gain: .0001 }) : null;
  if (curMusic) { curMusic.music = true; curMusic.gn.gain.setTargetAtTime(1, AC.currentTime, .8); }
}
let ambient = {};
function amb(name, on, gain = .5) {
  if (!AC) return;
  if (on && !ambient[name]) { const o = play(name, { loop: true, gain: .0001 }); if (!o) return; o.music = true; o.gn.gain.setTargetAtTime(gain, AC.currentTime, .4); ambient[name] = o; }
  if (!on && ambient[name]) { const o = ambient[name]; o.gn.gain.setTargetAtTime(0, AC.currentTime, .3); setTimeout(() => { try { o.s.stop(); } catch {} }, 1500); delete ambient[name]; }
}
function duck(on) { if (musicG) musicG.gain.setTargetAtTime(on ? .22 : .55, AC.currentTime, .15); }

/* ---------- время (останавливается, когда вкладка скрыта) ---------- */
let clockT = 0, lastP = performance.now(), paused = false;
function now() { return clockT; }
document.addEventListener('visibilitychange', () => { paused = document.hidden; if (AC) document.hidden ? AC.suspend() : AC.resume(); });

/* ---------- состояние ---------- */
let mode = 'menu', scene = null, sceneId = '', bi = 0, beat = null, bT0 = 0, bDur = 0, sched = [], fired = new Set(), onBeatEnd = null;
let gen = 0;   // номер текущей сцены: старые таймеры и мини-игры по нему понимают, что устарели
let flashT = -9, scareAt = null, shakeAmp = 0, typed = null, hb = 0;
const seen = new Set(LS.get('seen', []));

function durOf(k) { return (TIM[k] && TIM[k].dur) || (BUF[k] && BUF[k].duration) || 2; }
// кто говорит: имя на табличке и цвет
const SPK = { 'ТЁМЫЧ': { c: '#cfd3da', cls: 'narr', sub: 'ты' }, 'СМЕНЩИК': { c: '#ffd27a' }, 'КАТЯ': { c: '#ff9fbc' }, 'КАТЯ?': { c: '#ff4f7e' },
  '???': { c: '#ff3030', cls: 'monster' }, 'МИХАЛЫЧ': { c: '#8fd0ff' }, 'КАМЕРА': { c: '#7fe0a0' }, 'ТЕЛЕФОН': { c: '#9aa3b5' } };
const VOICE_WHO = { ch_guard: 'СМЕНЩИК', ch_katya: 'КАТЯ', ch_whisper: 'КАТЯ?', ch_monster: '???' };
const whoOf = k => VOICE_WHO[k] || 'ТЁМЫЧ';
let waiting = false, holding = false, auto = LS.get('auto', false);

function startScene(id) {
  gen++; stopAll(); choiceEl.classList.add('hide'); mgEl.classList.add('hide'); mgEl.innerHTML = '';
  uiEl.innerHTML = ''; $('hud').classList.remove('hide');
  sceneId = id; scene = S.scenes[id]; mode = 'play';
  if (!id.startsWith('over_')) LS.set('save', id);
  if (scene.music) music(scene.music);
  if (scene.time) { clockEl.textContent = 'НОЧЬ 1 · ' + scene.time; clockEl.classList.remove('hide'); }
  bi = -1; nextBeat();
}
function nextBeat() {
  bi++; amb('heart', false); holding = false; dlgEl.classList.remove('wait');
  if (!scene.beats[bi]) { seen.add(sceneId); LS.set('seen', [...seen]); return runThen(scene.then); }
  beat = scene.beats[bi]; bT0 = now(); fired.clear(); sched = []; typed = null; scareAt = null; hb = 0;
  chapEl.classList.toggle('hide', !beat.chapter); if (beat.chapter) chapEl.innerHTML = `<b>${beat.chapter[0]}</b><span>${beat.chapter[1]}</span>`;
  let t = .15;
  for (const k of beat.say || []) {
    sched.push({ t, k, d: durOf(k) });
    if (beat.scareGap && k === beat.say[0]) scareAt = t + gapTime(k);
    t += durOf(k) + .3;
  }
  if (beat.text) { const d = Math.max(2.2, beat.text.length / 14 + 1.3); typed = { t0: beat.say ? t : .2, d, s: beat.text }; t = Math.max(t, typed.t0 + d); }
  if (beat.scare != null) scareAt = beat.scare;
  bDur = Math.max(t + (beat.pad || .3), 1.2);
  ruleEl.classList.toggle('hide', !beat.rule); if (beat.rule) { ruleEl.textContent = beat.rule; learnRule(beat.rule); }
  if (beat.wait) { waiting = true; tapEl.textContent = '▶ ' + beat.wait; tapEl.classList.remove('hide'); dlgEl.classList.add('hide'); ruleEl.classList.add('hide'); return; }
  beatAudio();
}
function beginBeat() { waiting = false; tapEl.classList.add('hide'); chapEl.classList.add('hide'); if (beat.rule) ruleEl.classList.remove('hide'); bT0 = now(); play('click'); beatAudio(); }
function beatAudio() {
  for (const [n, at] of beat.sfx || []) if (n === 'static') amb('static', true, .25); else play(n, { when: at });
  for (const it of sched) { const o = play(it.k, { when: it.t, bus: 'voice' }); if (o) o.voice = true; }
  amb('rain', beat.fx === 'rain', .35); if (beat.fx !== 'static' && !(beat.sfx || []).some(s => s[0] === 'static')) amb('static', beat.fx === 'static', .3);
  if (scareAt != null) play('scare', { when: scareAt });
  if (bi > 0 && beat.img !== scene.beats[bi - 1].img) play('boom', { gain: .35 });
}
function gapTime(k) {   // начало самой длинной паузы между словами (там скример)
  const w = (TIM[k] || {}).w || []; let best = 0, at = durOf(k) / 2;
  for (let i = 1; i < w.length; i++) { const gp = w[i][0] - w[i - 1][0]; if (gp > best) { best = gp; at = w[i][0] - .15; } }
  return at;
}
function skipBeat() { if (mode !== 'play') return; stopAll(); duck(false); waiting = false; tapEl.classList.add('hide'); nextBeat(); }
// тап по экрану: начать кадр / дочитать реплику / дальше
function tap() {
  if (mode !== 'play' || !beat) return;
  if (waiting) return beginBeat();
  if (holding) return nextBeat();
  const t = now() - bT0;
  if (scareAt != null && t < scareAt + 1) return;            // скример не пропускается
  for (const o of [...playing]) if (o.voice) { try { o.s.stop(); } catch {} playing.delete(o); }
  bT0 = now() - bDur;                                        // сразу к концу кадра (реплика показана целиком)
}
const known = new Set(LS.get('rules', []));
function learnRule(r) { if (/^\d\./.test(r) || r.startsWith('«')) { known.add(r); LS.set('rules', [...known]); } }
function showRules() {
  const list = [...known].sort();
  noteEl.innerHTML = `<div>${list.length ? list.filter(r => /^\d/.test(r)).join('\n\n') + list.filter(r => !/^\d/.test(r)).map(r => `<i>${r}</i>`).join('') : 'Правил пока нет.'}<small>нажми, чтобы закрыть</small></div>`;
  noteEl.classList.remove('hide'); noteEl.onclick = () => noteEl.classList.add('hide');
}

/* ---------- что дальше ---------- */
function runThen(th) {
  dlgEl.classList.add('hide'); tapEl.classList.add('hide'); chapEl.classList.add('hide'); ruleEl.classList.add('hide'); amb('rain', false); amb('static', false); amb('heart', false);
  if (th.goto) return startScene(th.goto);
  if (th.choice) return showChoice(th);
  if (th.camgame) return camGame(th.camgame);
  if (th.mg) return MG[th.mg](th);
  if (th.ending) return endScreen(th);
  if (th.over) return overScreen(th);
}
function showChoice(th) {
  mode = 'choice'; music('tension');
  choiceEl.innerHTML = `<h2>${th.choice}</h2>` + th.opts.map((o, i) => `<button data-i="${i}"><b>${i + 1}</b>${o.t}</button>`).join('') + (th.timeout ? '<div id="timer"><i></i></div>' : '');
  choiceEl.classList.remove('hide');
  let done = false, t0 = now(), lastTick = -1; const my = gen;
  const pick = i => { if (done || my !== gen) return; done = true; play('click'); choiceEl.classList.add('hide'); startScene(th.opts[i].go); };
  choiceEl.querySelectorAll('button').forEach(b => b.onclick = () => pick(+b.dataset.i));
  if (th.timeout) {
    const bar = choiceEl.querySelector('#timer i');
    (function loop() {
      if (done || my !== gen) return; const left = th.timeout - (now() - t0);
      bar.style.width = Math.max(0, left / th.timeout * 100) + '%';
      const s = Math.ceil(left); if (s <= 3 && s !== lastTick && s > 0) { lastTick = s; play('tick', { gain: 2 }); }
      if (left <= 0) return pick(th.def == null ? Math.floor(Math.random() * th.opts.length) : th.def); requestAnimationFrame(loop);
    })();
  }
}

/* ---------- мини-игра: обход камер ---------- */
let camView = null;
function camGame(next) {
  mode = 'cams'; music('drone'); amb('static', true, .08);
  let seenCams = new Set(), warned = false; const my = gen;
  const draw = () => {
    mgEl.innerHTML = `<div class="mghead">ОБХОД КАМЕР<small>Проверь три камеры. Помни правило четыре.</small></div><div class="camgrid">` +
      S.cams.map((c, i) => c.forbidden ? `<div class="cam off" data-i="${i}"><b>${c.id}</b><span>ВЫКЛ</span></div>`
        : `<div class="cam ${seenCams.has(i) ? 'seen' : ''}" data-i="${i}" style="background-image:url(img/${c.img}.webp)"><b>${c.id} ${c.name}</b></div>`).join('') + '</div>' +
      `<div class="mgfoot">${seenCams.size >= 3 ? '<button class="btn" id="cgo" style="text-align:center">Продолжить смену</button>' : `<div style="text-align:center;opacity:.7;font-size:3.6cqw">Проверено: ${seenCams.size} из 3</div>`}</div>`;
    mgEl.querySelectorAll('.cam').forEach(el => el.onclick = () => {
      const i = +el.dataset.i, c = S.cams[i];
      if (c.forbidden) {
        if (!warned) { warned = true; play('static'); mgEl.classList.add('flash'); setTimeout(() => mgEl.classList.remove('flash'), 300);
          el.innerHTML = '<b>CAM 13</b><span style="font-size:3.6cqw">ТОЧНО ВКЛЮЧИТЬ?<br>ПРАВИЛО 4</span>'; return; }
        amb('static', false); mgEl.classList.add('hide'); return startScene('over_cam13');
      }
      play('click'); play('static', { gain: .5 });
      camView = { c, t0: now() }; mgEl.classList.add('hide');
      say('КАМЕРА', c.note, c.note.length);
      setTimeout(() => { if (my !== gen) return; camView = null; dlgEl.classList.add('hide'); seenCams.add(i); if (c.creep) play('boom', { gain: .4 }); mgEl.classList.remove('hide'); draw(); }, 2600);
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
    mgEl.innerHTML = `<div class="mghead">${th.title}<small>${th.hint}</small></div><div class="mgfoot" style="bottom:72%"><div class="meter"><i id="air"></i></div><div style="text-align:center;font-size:3.4cqw;margin-top:1.5cqw;opacity:.7">воздух</div></div><button class="bigbtn" id="hb">ДЕРЖИ</button>`;
    mgEl.classList.remove('hide');
    const btn = $('hb'), air = $('air'); let held = false, a = 1, t0 = now(), safe = false, end = false, steps = 0;
    const dur = th.dur || 9, set = h => { held = h; btn.classList.toggle('held', h); btn.textContent = h ? 'НЕ ДЫШИ' : 'ДЕРЖИ'; };
    btn.onpointerdown = e => { e.preventDefault(); set(true); }; btn.onpointerup = btn.onpointerleave = btn.onpointercancel = () => set(false);
    const fin = ok => { if (end || my !== gen) return; end = true; amb('heart', false); mgEl.classList.add('hide'); startScene(ok ? th.win : th.lose); };
    (function loop() {
      if (end || my !== gen) return; const t = now() - t0;
      if (t > .8 && Math.floor(t / 1.1) > steps && t < dur) { steps++; play('steps', { gain: .5 + .5 * Math.sin(t) }); if (steps % 3 === 0) play('ch_whisper', { bus: 'voice', gain: .6 }); }
      a = Math.max(0, Math.min(1, a + (held ? -0.085 : 0.25) / 60)); air.style.width = a * 100 + '%';
      if (t > 2.5 && t < dur && !held) return fin(false);      // выдохнул — услышала
      if (a <= 0) return fin(false);                            // задохнулся
      if (t >= dur && !safe) { safe = true; mgEl.querySelector('.mghead').innerHTML = 'ТИХО…<small>Шаги ушли. Можно дышать.</small>'; }
      if (safe && !held) return fin(true);
      requestAnimationFrame(loop);
    })();
  },
  // держать дверь: часто жать, полоса не должна упасть до нуля
  door(th) {
    mode = 'mg'; music('tension'); const my = gen;
    mgEl.innerHTML = `<div class="mghead">${th.title}<small>${th.hint}</small></div><div class="mgfoot" style="bottom:72%"><div class="meter"><i id="dr"></i></div></div><button class="bigbtn" id="db">ДЕРЖИ<br>ДВЕРЬ</button>`;
    mgEl.classList.remove('hide');
    let v = .6, t0 = now(), end = false, nextHit = .5; const bar = $('dr');
    $('db').onpointerdown = e => { e.preventDefault(); v = Math.min(1, v + .075); shakeAmp = 10; };
    const fin = ok => { if (end || my !== gen) return; end = true; mgEl.classList.add('hide'); startScene(ok ? th.win : th.lose); };
    (function loop() {
      if (end || my !== gen) return; const t = now() - t0;
      if (t > nextHit) { play('knock', { gain: 1.2 }); shakeAmp = 26; nextHit = t + .9 + Math.random() * .6; if (Math.random() < .3) play('ch_katya', { bus: 'voice', gain: .5 }); }
      v -= (.0042 + t * .00022); bar.style.width = Math.max(0, v * 100) + '%';
      if (v <= 0) return fin(false); if (t > (th.dur || 12)) return fin(true);
      requestAnimationFrame(loop);
    })();
  },
  // бег: быстро выбрать сторону (подсказка — где гаснет свет, туда нельзя)
  run(th) {
    mode = 'mg'; music('tension'); const my = gen; let k = 0, end = false; const N = th.n || 5;
    const fin = ok => { if (end || my !== gen) return; end = true; mgEl.classList.add('hide'); startScene(ok ? th.win : th.lose); };
    const step = () => {
      if (k >= N) return fin(true);
      const bad = Math.random() < .5 ? 'L' : 'R';
      mgEl.innerHTML = `<div class="mghead">${th.title}<small>Свет гаснет ${bad === 'L' ? 'СЛЕВА' : 'СПРАВА'}. Беги в другую сторону! (${k + 1}/${N})</small></div><div class="mgfoot" style="bottom:72%"><div class="meter"><i id="rt" style="width:100%"></i></div></div><div class="arrows"><button data-d="L">◀</button><button data-d="R">▶</button></div>`;
      mgEl.classList.remove('hide'); play('steps', { gain: .8 }); shakeAmp = 14;
      const t0 = now(), lim = Math.max(1.1, 2.2 - k * .2); let ok = false;
      mgEl.querySelectorAll('.arrows button').forEach(b => b.onclick = () => { if (ok) return; ok = true; if (b.dataset.d === bad) return fin(false); play('click'); k++; step(); });
      (function loop() { if (end || ok || my !== gen) return; const l = lim - (now() - t0); const el = $('rt'); if (el) el.style.width = Math.max(0, l / lim * 100) + '%'; if (l <= 0) return fin(false); requestAnimationFrame(loop); })();
    };
    step();
  }
};

/* ---------- экраны ---------- */
function gallery() {
  const got = LS.get('endings', []), ov = LS.get('overs', []);
  return `<div class="gal">${Object.entries(S.endings).map(([k, n]) => `<div class="${got.includes(k) ? 'got' : ''}">${got.includes(k) ? n : '???'}</div>`).join('')}</div>
    <small>Концовки: ${got.length} из ${Object.keys(S.endings).length} · Смертей найдено: ${ov.length} из ${S.overs.length}</small>`;
}
/* ---------- спонсоры игры (донаты звёздами Telegram через бота) ---------- */
const API = /railway\.app$|^localhost$/.test(location.hostname) ? '' : 'https://office-dreams-production.up.railway.app';
let SP = { donate: 'https://t.me/officedream1_bot?start=games', list: [] };
get(API + '/api/games/sponsors', 'json').then(j => { if (j && j.ok) SP = Object.assign(SP, j, { donate: j.donate || SP.donate }); }).catch(() => {});
const escH = v => String(v).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
function sponsorsHtml() {
  const names = SP.list.slice(0, 30).map(r => `<span>${escH(r.name)}</span>`).join('');
  return `<div class="spons"><b>СПОНСОРЫ ИГРЫ</b>${names || '<i>Стань первым — твоё имя будет здесь</i>'}</div>`;
}
const supportBtn = () => `<a class="btn ghost sup" href="${escH(SP.donate)}" target="_blank" rel="noopener">⭐ Поддержать игру</a>`;
function menu() {
  gen++; mode = 'menu'; stopAll(false); music(null); for (const k in ambient) amb(k, false); $('hud').classList.add('hide');
  choiceEl.classList.add('hide'); mgEl.classList.add('hide'); dlgEl.classList.add('hide'); tapEl.classList.add('hide'); chapEl.classList.add('hide'); clockEl.classList.add('hide'); noteEl.classList.add('hide'); ruleEl.classList.add('hide'); waiting = false;
  const save = LS.get('save', null);
  uiEl.innerHTML = `<div class="screen"><h1>НОЧНАЯ<br>СМЕНА<em>ИНТЕРАКТИВНЫЙ ХОРРОР</em></h1>
    <p>Четыре правила. Одна ночь. Сестра за стеклом.<br>Твои выборы решают, доживёшь ли ты до утра.</p>
    <button class="btn" id="mNew">${save ? 'Начать заново' : 'Начать смену'}</button>
    ${save ? '<button class="btn ghost" id="mCont">Продолжить</button>' : ''}
    <button class="btn ghost" id="mGal">Концовки</button>
    ${supportBtn()}
    <small>Лучше в наушниках и в темноте.</small></div>`;
  $('mNew').onclick = () => begin('intro'); if (save) $('mCont').onclick = () => begin(save);
  $('mGal').onclick = () => { uiEl.innerHTML = `<div class="screen"><h1 style="font-size:9cqw">КОНЦОВКИ</h1>${gallery()}<button class="btn ghost" id="mBack" style="margin-top:6cqw">Назад</button></div>`; $('mBack').onclick = menu; };
}
async function begin(id) {
  unlockAudio();   // до любого await, пока идёт нажатие
  uiEl.innerHTML = `<div class="screen"><small>Загрузка звука…</small></div>`;
  await Promise.race([initAudio(), new Promise(r => setTimeout(r, 8000))]);   // звук не успел — играем дальше, без зависания
  uiEl.innerHTML = ''; startScene(id);
}
function endScreen(th) {
  clockEl.classList.add('hide'); $('hud').classList.add('hide');
  mode = 'end'; const got = new Set(LS.get('endings', [])); got.add(th.ending); LS.set('endings', [...got]); LS.set('save', null);
  play('boom'); music('drone');
  uiEl.innerHTML = `<div class="screen"><h1 style="font-size:9cqw">${th.title}<em>${S.endings[th.ending].toUpperCase()}</em></h1><p>${th.sub}</p>${gallery()}
    <button class="btn" id="eAgain" style="margin-top:5cqw">Сыграть ещё раз</button><button class="btn ghost" id="eMenu">Меню</button>${supportBtn()}${sponsorsHtml()}</div>`;
  $('eAgain').onclick = () => { uiEl.innerHTML = ''; startScene('intro'); }; $('eMenu').onclick = menu;
}
function overScreen(th) {
  clockEl.classList.add('hide'); $('hud').classList.add('hide');
  mode = 'end'; const ov = new Set(LS.get('overs', [])); ov.add(th.over); LS.set('overs', [...ov]);
  music(null);
  uiEl.innerHTML = `<div class="screen over"><h1>${th.over}</h1><p>${th.sub}<br>Ты не дожил до утра.</p>
    <button class="btn" id="oRetry">Попробовать снова</button><button class="btn ghost" id="oMenu">Меню</button>
    <small>Смертей найдено: ${ov.size} из ${S.overs.length}</small></div>`;
  $('oRetry').onclick = () => { uiEl.innerHTML = ''; startScene(th.retry); }; $('oMenu').onclick = menu;
}

/* ---------- рисование ---------- */
const noise = document.createElement('canvas'); noise.width = noise.height = 256;
{ const n = noise.getContext('2d'), d = n.createImageData(256, 256); for (let i = 0; i < d.data.length; i += 4) { const v = Math.random() * 255; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; } n.putImageData(d, 0, 0); }
const drops = Array.from({ length: 140 }, () => ({ x: Math.random() * W, y: Math.random() * H, v: 1800 + Math.random() * 1400, l: 40 + Math.random() * 70 }));
function cover(im, cx, cy, z, ox = 0, oy = 0) {
  if (!im || !im.width) return;
  const s = Math.max(W / im.width, H / im.height) * z, w = im.width * s, h = im.height * s;
  let x = W / 2 - cx * w, y = H / 2 - cy * h; x = Math.min(0, Math.max(W - w, x)); y = Math.min(0, Math.max(H - h, y));
  g.drawImage(im, x + ox, y + oy, w, h);
}
const ease = t => t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
function cctv(label, t) {
  g.save(); g.globalCompositeOperation = 'saturation'; g.fillStyle = 'rgba(128,128,128,.78)'; g.fillRect(0, 0, W, H);
  g.globalCompositeOperation = 'multiply'; g.fillStyle = 'rgb(200,235,215)'; g.fillRect(0, 0, W, H); g.restore();
  grain(.16); g.fillStyle = 'rgba(255,255,255,.06)'; g.fillRect(0, (t * 240) % H, W, 7);
  g.font = '700 46px Inter, Arial, sans-serif'; g.fillStyle = 'rgba(255,255,255,.85)'; g.textBaseline = 'top';
  if (label) g.fillText(label, 60, 150);
  const [hh, mm] = ((scene && scene.time) || '03:21').split(':').map(Number), sec = 7 + Math.floor(t), m2 = mm + Math.floor(sec / 60), tx = `17.10  ${String(hh + Math.floor(m2 / 60)).padStart(2, '0')}:${String(m2 % 60).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`;
  g.textAlign = 'right'; g.fillText(tx, W - 60, 150); g.textAlign = 'left';
  if (t % 1 < .6) { g.fillStyle = '#e22'; g.beginPath(); g.arc(78, 236, 13, 0, 7); g.fill(); g.fillText('REC', 104, 214); }
}
function grain(a) { g.save(); g.globalAlpha = a; g.globalCompositeOperation = 'overlay'; const ox = Math.random() * 256, oy = Math.random() * 256; g.translate(-ox, -oy); g.fillStyle = g.createPattern(noise, 'repeat'); g.fillRect(0, 0, W + 256, H + 256); g.restore(); }
function vignette() { const r = g.createRadialGradient(W / 2, H / 2, H * .25, W / 2, H / 2, H * .75); r.addColorStop(0, 'rgba(0,0,0,0)'); r.addColorStop(1, 'rgba(0,0,0,.8)'); g.fillStyle = r; g.fillRect(0, 0, W, H); }
function phone(kind, t) {
  g.fillStyle = '#030405'; g.fillRect(0, 0, W, H);
  const x = 240, y = 230, w = 600, h = 1060; g.fillStyle = '#111'; rr(x - 20, y - 20, w + 40, h + 40, 70); g.fillStyle = '#0b0d12'; rr(x, y, w, h, 54);
  g.textAlign = 'center'; g.fillStyle = '#fff';
  if (kind === 'msg') {
    g.font = '600 34px Inter, Arial'; g.fillStyle = '#9aa'; g.fillText('Катя', W / 2, y + 120);
    g.fillStyle = '#2b2f3a'; rr(x + 50, y + 420, w - 120, 190, 36); g.fillStyle = '#fff'; g.font = '600 40px Inter, Arial'; g.textAlign = 'left';
    g.fillText('Не открывай им.', x + 90, y + 500); if (t > 1.6) g.fillText('Это не я.', x + 90, y + 560);
    if (t > 2.4) { g.fillStyle = '#2b2f3a'; rr(x + 50, y + 650, w - 220, 120, 36); g.fillStyle = '#f66'; g.fillText('Я тебя вижу', x + 90, y + 725); }
  } else {
    const pulse = 1 + .06 * Math.sin(t * 8);
    g.font = '700 70px Inter, Arial'; g.fillText('Катя', W / 2, y + 330); g.font = '500 36px Inter, Arial'; g.fillStyle = '#9aa';
    g.fillText(kind === 'call2' ? '00:' + String(Math.floor(t) + 8).padStart(2, '0') : 'мобильный · входящий', W / 2, y + 400);
    g.fillStyle = '#2a3'; g.beginPath(); g.arc(x + w - 150, y + h - 200, 70 * (kind === 'call2' ? 1 : pulse), 0, 7); g.fill();
    g.fillStyle = '#d33'; g.beginPath(); g.arc(x + 150, y + h - 200, 70, 0, 7); g.fill();
  }
  g.textAlign = 'left'; grain(.08);
}
function rr(x, y, w, h, r) { g.beginPath(); g.roundRect ? g.roundRect(x, y, w, h, r) : g.rect(x, y, w, h); g.fill(); }
let lastSubKey = '';
function plate(who) {
  const sp = SPK[who] || SPK['ТЁМЫЧ']; dlgEl.style.setProperty('--spk', sp.c);
  dlgEl.className = (sp.cls || '') + (holding ? ' wait' : ''); dlgEl.querySelector('.nm').innerHTML = who + (sp.sub ? `<small>${sp.sub}</small>` : '');
}
function say(who, text, k) {   // печатающийся текст
  const key = who + '|' + k + '|' + text; if (key === lastSubKey) return; lastSubKey = key;
  plate(who); dlgEl.querySelector('.ln').textContent = text.slice(0, k);
}
const chunkCache = {};
function chunks(k, w) {   // куски из таймингов без знаков препинания: берём те же слова из полного текста реплики
  if (chunkCache[k]) return chunkCache[k];
  const full = ((TIM[k] || {}).t || '').split(/\s+/).filter(Boolean); let i = 0;
  const out = w.map(x => { const n = x[1].split(/\s+/).length, part = full.slice(i, i + n).join(' '); i += n; return part || x[1]; });
  return (chunkCache[k] = i === full.length ? out : w.map(x => x[1]));
}
function sayWords(who, words, on) {   // озвученная реплика целиком, произнесённые слова ярче
  const key = who + '|' + on + '|' + words.join(' '); if (key === lastSubKey) return; lastSubKey = key;
  plate(who); dlgEl.querySelector('.ln').innerHTML = words.map((w, i) => `<span class="${i < on ? 'on' : ''}">${w}</span>`).join(' ');
}
function frame(tp) {
  const dt = Math.min(.05, (tp - lastP) / 1000); lastP = tp; if (!paused) clockT += dt;
  const T = now();
  if (mode === 'play' && beat) {
    let t = waiting ? 0 : T - bT0;
    if (t >= bDur) {
      const silent = !(beat.say || []).length && !beat.text;
      if (auto || silent || beat.auto) { nextBeat(); return requestAnimationFrame(frame); }
      if (!holding) { holding = true; dlgEl.classList.add('wait'); }
      t = bDur;
    }
    const p = Math.min(1, t / bDur);
    g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    // скример: вспышка, рывок, сильная тряска
    const sc = scareAt != null && t >= scareAt, st = sc ? t - scareAt : 0;
    if (sc && st < .05) { flashT = T; shakeAmp = 60; }
    const sx = (Math.sin(T * 1.6) * 8 + Math.sin(T * 4.9) * 3) + (Math.random() - .5) * shakeAmp, sy = (Math.cos(T * 1.2) * 8 + Math.sin(T * 4.1) * 3) + (Math.random() - .5) * shakeAmp;
    shakeAmp *= .9;
    if (beat.phone) phone(beat.phone, t);
    else if (beat.img) {
      const cams = beat.cams || [[.5, .5, 1, 1.1]], n = cams.length, ci = Math.min(n - 1, Math.floor(p * n)), lp = p * n - ci, c = cams[ci];
      let z = c[2] + (c[3] - c[2]) * ease(lp); if (sc) z = Math.max(z, c[3]) * 1.3;
      cover(IMG[beat.img], c[0], c[1], z * 1.04, sx, sy);
      if (ci !== beat._ci) { beat._ci = ci; if (ci > 0) flashT = T - .18; }
      if (beat.fx === 'cctv' || beat.fx === 'static') cctv(beat.label, t);
      if (beat.fx === 'static' && t > 1.2) { grain(.55); g.fillStyle = `rgba(255,255,255,${.04 + .04 * Math.sin(t * 40)})`; g.fillRect(0, 0, W, H); }
      if (beat.fx === 'rain') { g.strokeStyle = 'rgba(200,215,230,.28)'; g.lineWidth = 2; g.beginPath(); for (const d of drops) { const y = (d.y + T * d.v) % (H + 100) - 100, x = (d.x + y * .12) % W; g.moveTo(x, y); g.lineTo(x + d.l * .12, y + d.l); } g.stroke(); grain(.08); }
      if (beat.fx === 'lamp' && (T * 3.1) % 9 < .25) { g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(0, 0, W, H); }
      if (beat.fx === 'flicker' && (T % 4) < .5) { g.fillStyle = `rgba(255,255,255,${.04 * Math.abs(Math.sin(T * 31))})`; g.fillRect(0, 0, W, H); }
      if (beat.fx === 'dawn') { g.save(); g.globalCompositeOperation = 'soft-light'; g.fillStyle = 'rgba(255,160,80,.75)'; g.fillRect(0, 0, W, H); g.globalCompositeOperation = 'screen'; g.fillStyle = `rgba(70,35,10,${.15 + .15 * p})`; g.fillRect(0, 0, W, H); g.restore(); grain(.06); }
      if (beat.fx === 'dark') { g.fillStyle = `rgba(0,0,0,${p * .85})`; g.fillRect(0, 0, W, H); }
      if (beat.light) { g.fillStyle = `rgba(0,0,0,${.35 * (1 - Math.min(1, t / 6))})`; g.fillRect(0, 0, W, H); }
      if (beat.floors) { const f = Math.min(7, 1 + Math.floor(t / .6)); g.fillStyle = 'rgba(0,0,0,.6)'; rr(W / 2 - 90, 220, 180, 150, 20); g.fillStyle = '#f33'; g.font = '800 110px Inter, Arial'; g.textAlign = 'center'; g.fillText(f, W / 2, 245); g.textAlign = 'left'; }
      if (!beat.fx) grain(.07);
      if (sc) { g.fillStyle = `rgba(160,0,0,${Math.max(0, .35 - st * .2)})`; g.fillRect(0, 0, W, H); }
      vignette();
    }
    // реплики: табличка с именем того, кто говорит; слова загораются по мере звучания
    let voiceOn = false, shown = false;
    if (!waiting) {
      if (typed && t >= typed.t0 && typed.s) {
        const k = Math.floor(Math.min(1, (t - typed.t0) / (typed.d - .9)) * typed.s.length);
        say(beat.who || 'ТЁМЫЧ', typed.s, k); shown = true;
        if (k < typed.s.length && Math.random() < .25) play('tick', { gain: .25 });
      } else {
        let cur = null; for (const it of sched) if (t >= it.t) cur = it;
        if (cur) {
          const tm = TIM[cur.k] || {}, w = tm.w || [], on = w.filter(x => t - cur.t >= x[0] - .02).length;
          voiceOn = t < cur.t + cur.d + .1; sayWords(whoOf(cur.k), chunks(cur.k, w), voiceOn ? on : w.length); shown = true;
        }
      }
    }
    if (!shown && !dlgEl.classList.contains('hide')) { dlgEl.classList.add('hide'); lastSubKey = ''; }
    duck(voiceOn);
    if (beat.heart && t > hb) { play('heart', { gain: .8 }); hb = t + Math.max(.55, .9 - t * .02); }
  } else if (mode === 'cams' && camView) {
    const t = T - camView.t0, c = camView.c; g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    cover(IMG[c.img], c.c[0], c.c[1], c.c[2] + t * .04); cctv(c.id + '  ' + c.name, t); vignette();
  } else if (mode !== 'play') {
    // фон меню/выбора: последний кадр затемнён, медленно дышит
    const im = IMG[(beat && beat.img) || 'f01']; g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    cover(im, .5, .5, 1.08 + .03 * Math.sin(T * .3)); g.fillStyle = 'rgba(0,0,0,.45)'; g.fillRect(0, 0, W, H); grain(.1); vignette();
  }
  if (T - flashT < .2) { g.fillStyle = `rgba(255,255,255,${(1 - (T - flashT) / .2) * .7})`; g.fillRect(0, 0, W, H); }
  requestAnimationFrame(frame);
}

/* ---------- старт ---------- */
$('bMute').onclick = () => { const m = !LS.get('mute', false); LS.set('mute', m); if (master) master.gain.value = m ? 0 : 1; $('bMute').textContent = m ? '🔇' : '🔊'; };
$('bMute').textContent = LS.get('mute', false) ? '🔇' : '🔊';
$('bMenu').onclick = () => menu();
$('bRules').onclick = e => { e.stopPropagation(); showRules(); };
$('bAuto').onclick = e => { e.stopPropagation(); auto = !auto; LS.set('auto', auto); $('bAuto').classList.toggle('on', auto); };
$('bAuto').classList.toggle('on', auto);
$('stage').addEventListener('pointerdown', e => { if (e.target.closest('button, #choice, #mg, #ui, #hud, #note')) return; tap(); });
document.addEventListener('keydown', e => { if (e.key === ' ' || e.key === 'Enter') tap(); if (mode === 'choice' && /^[1-4]$/.test(e.key)) { const b = choiceEl.querySelector(`button[data-i="${+e.key - 1}"]`); if (b) b.click(); } if (e.key === 'Escape') menu(); });
uiEl.innerHTML = `<div class="screen"><h1>НОЧНАЯ<br>СМЕНА</h1><div id="loadbar"><i></i></div><small>Загрузка…</small></div>`;
requestAnimationFrame(frame);
window.addEventListener('error', e => { if (mode === 'menu' && !uiEl.innerHTML) uiEl.innerHTML = `<div class="screen"><p>Ошибка: ${String(e.message || e).slice(0, 120)}</p></div>`; });
load(p => { const b = document.querySelector('#loadbar i'); if (b) b.style.width = p * 100 + '%'; }).catch(() => {}).then(() => { initAudio(); menu(); });
window.__ns = { back: () => menu(), startScene, get mode() { return mode; }, get scene() { return sceneId; }, skipBeat };
})();
