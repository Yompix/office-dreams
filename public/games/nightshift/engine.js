/* «Ночная смена» — движок интерактивного кино: кадры с движением камеры и эффектами, голос, субтитры, выборы, мини-игры.
   Звук: живой фон места (никогда не цифровая тишина), случайные далёкие звуки, тишина перед каждым скримером,
   «подпись» Жильца перед появлением, стук Кати по стеклу в такт её руке, слой обертонов для динамика телефона.
   Поля кадра (story.js) сверх старых: sms {from, text, at}, fx 'glitch', subl (кадр-вспышка лица), props, cctv. */
(() => {
'use strict';
const S = window.STORY, $ = id => document.getElementById(id);
const cv = $('cv'), g = cv.getContext('2d'), W = 1080, H = 1920;
const dlgEl = $('dlg'), tapEl = $('tap'), chapEl = $('chap'), clockEl = $('clock'), noteEl = $('note'), ruleEl = $('rule'), choiceEl = $('choice'), uiEl = $('ui'), mgEl = $('mg'), smsEl = $('sms');
const LS = { get(k, d) { try { const v = localStorage.getItem('ns1_' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
             set(k, v) { try { localStorage.setItem('ns1_' + k, JSON.stringify(v)); } catch {} } };
const calm = () => !!LS.get('calm', false);   // настройка «меньше вспышек»
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
  msg: '<svg viewBox="0 0 24 24"><path class="f" d="M3 5h18v12H9l-6 4z"/></svg>'
};

/* ---------- загрузка ---------- */
const BUF = {}; let TIM = {};
const SNDS = ['drone', 'tension', 'boom', 'scare', 'knock', 'ding', 'heart', 'rain', 'static', 'buzz', 'ring', 'tick', 'click', 'door', 'steps',
  'ch_guard', 'ch_katya', 'ch_whisper', 'ch_monster', ...Array.from({ length: 17 }, (_, i) => 'l' + String(i + 1).padStart(2, '0'))];
// звуковые эффекты (ElevenLabs): докачиваются в фоне после старта
const SFX2 = ['room_tone', 'drone_dread', 'sub_presence', 'breath_close', 'neck_crack', 'steps_barefoot', 'eat_wet', 'whispers', 'metal_groan', 'glass_tap', 'door_slams', 'reverse_swell', 'fluor_flicker', 'heart_held', 'hum_stop', 'phone_crackle', 'lift_ding_far', 'tinnitus', 'jumpscare', 'glass_slam', 'cleaner_cry_a', 'cleaner_cry_b'];
let directG = null, AC = null, revIn = null, muffle = null, master, musicG, voiceG, sfxG, lowSend = null, musicSend = null, rawSnd = {}, analyser = null, abuf = null, talkV = 0;
// громкость голоса сейчас (для движения рта)
function voiceLevel() { if (!analyser) return 0; analyser.getByteTimeDomainData(abuf); let q = 0; for (let i = 0; i < abuf.length; i += 2) { const v = (abuf[i] - 128) / 128; q += v * v; } return Math.min(1, Math.sqrt(q / (abuf.length / 2)) * 6); }
// XHR вместо fetch: так файлы читаются и внутри Android-приложения (file://)
function get(url, type) { return new Promise((ok, no) => { const x = new XMLHttpRequest(); x.open('GET', url); x.responseType = type; x.onload = () => (x.status === 200 || x.status === 0) && x.response ? ok(x.response) : no(); x.onerror = no; x.send(); }); }
let loadP = 0;
async function load(onp) {
  let done = 0; const total = SNDS.length + 2 + 6 * 4, tick = (n = 1) => onp(Math.min(1, (done += n) / total));   // модели тяжёлые: каждая считается за 4
  // 3D-мир строится кодом прямо здесь; заодно прогреваем шейдеры, чтобы первая сцена не дёргалась
  const models = window.__W3chars && window.__W3chars.load ? window.__W3chars.load(() => tick(4)).catch(() => false) : Promise.resolve(false);
  const world = models.then(() => new Promise(r => setTimeout(() => { try { if (window.World && World.init()) { World.vhs = VHS_LV[LS.get('vhs', 2)] ?? .7; World.warm(); } } catch (e) { console.error(e); } worldTried = true; tick(); r(); }, 30)));
  const sn = SNDS.map(k => get('snd/' + k + '.mp3', 'arraybuffer').then(b => { rawSnd[k] = b; tick(); }).catch(tick));
  const tm = get('timing.json?v=5', 'json').then(j => { TIM = j || {}; tick(); }).catch(tick);
  await Promise.all([world, ...sn, tm]);
  // остальные реплики (их много) докачиваются в фоне, уже во время игры
  for (const k of [...SFX2, ...Object.keys(TIM)]) if (!SNDS.includes(k)) get('snd/' + k + '.mp3', 'arraybuffer').then(b => { rawSnd[k] = b; if (AC) decodeOne(k, b); }).catch(() => {});
}
// голос в пространстве: внутренний голос героя — сухой и близкий; остальные звучат в помещении;
// Катя за стеклом — глухо; «Катя» — вдруг чисто и сухо, будто уже по эту сторону, на полтона ниже, с низким призраком под голосом
const GLASS = /door|katya|smile|entrance/;
function voiceFx(k) {
  if (!AC || !revIn) return [{}];
  const who = whoOf(k), img = (beat && beat.img) || '', aloud = ((TIM[k] || {}).t || '').trim().startsWith('—');
  if (who === 'ТЁМЫЧ') return [aloud ? { to: [[voiceG, 1], [revIn, .12]] } : {}];
  if (who === 'КАТЯ') return [GLASS.test(img) ? { to: [[muffle, 1.4], [revIn, .2]] } : { to: [[voiceG, 1], [revIn, .15]] }];
  if (who === 'КАТЯ?') return [{ rate: .95, to: [[voiceG, 1], [revIn, .05]] }, { rate: .5, gain: .1, dt: .03, cut: durOf(k), to: [[voiceG, 1]] }];
  if (who === '???') return [{ rate: .97, to: [[voiceG, 1], [revIn, .3]] }, { rate: .5, gain: .16, cut: durOf(k), to: [[voiceG, 1], [revIn, .3]] }];
  if (who === 'МИХАЛЫЧ' && /server_door|basement/.test(img)) return [{ to: [[muffle, 1.5], [revIn, .35]] }];
  return [{ to: [[voiceG, 1], [revIn, .2]] }];
}
function decodeOne(k, b) { return new Promise(r => { try { const pr = AC.decodeAudioData(b.slice(0), d => { BUF[k] = d; r(); }, () => r()); if (pr && pr.catch) pr.catch(() => r()); } catch { r(); } }); }
let decoding = null;
const filt = (type, f, q) => { const n = AC.createBiquadFilter(); n.type = type; n.frequency.value = f; if (q != null) n.Q.value = q; return n; };
function initAudio() {   // контекст создаём заранее (он «спит» до нажатия), звуки раскодируем в фоне
  if (decoding) return decoding;
  const Ctx = window.AudioContext || window.webkitAudioContext; if (!Ctx) return (decoding = Promise.resolve());
  AC = new Ctx();
  const muted = LS.get('mute', false);
  master = AC.createGain(); const comp = AC.createDynamicsCompressor(); comp.threshold.value = -16; comp.ratio.value = 4; comp.attack.value = .004; comp.release.value = .25; master.connect(comp); comp.connect(AC.destination); master.gain.value = muted ? 0 : 1;
  // крик скримера — мимо компрессора, чтобы был громче голоса; резкие 4–5 кГц чуть приглушены (динамик телефона их режет в уши)
  directG = AC.createGain(); directG.gain.value = master.gain.value; { const pk = filt('peaking', 4500, 1.2); pk.gain.value = -4; directG.connect(pk); pk.connect(AC.destination); }
  musicG = AC.createGain(); musicG.gain.value = .55; musicG.connect(master);
  voiceG = AC.createGain(); voiceG.gain.value = 1; voiceG.connect(master);
  sfxG = AC.createGain(); sfxG.gain.value = .8; sfxG.connect(master);
  // слой для динамика телефона: он не играет ниже ~250 Гц, поэтому гул, дрон и сердце получают мягкие обертоны 200–1400 Гц
  { lowSend = AC.createGain(); const lp = filt('lowpass', 260, .7), dr = AC.createGain(), ws = AC.createWaveShaper(), hp = filt('highpass', 200, .7), lp2 = filt('lowpass', 1400, .7), wet = AC.createGain();
    const n = 1024, cu = new Float32Array(n); for (let i = 0; i < n; i++) { const x = i / (n - 1) * 2 - 1; cu[i] = Math.tanh(x * 3) / Math.tanh(3); }
    ws.curve = cu; ws.oversample = 'none'; dr.gain.value = 2; wet.gain.value = .6;
    lowSend.connect(lp); lp.connect(dr); dr.connect(ws); ws.connect(hp); hp.connect(lp2); lp2.connect(wet); wet.connect(master);
    musicSend = AC.createGain(); musicSend.gain.value = 1; musicG.connect(musicSend); musicSend.connect(lowSend); }
  // пространство: реверберация (импульс — шум с затуханием) и «за стеклом/дверью» — глухой фильтр
  { const len = AC.sampleRate * 2.4 | 0, ir = AC.createBuffer(2, len, AC.sampleRate);
    for (let c = 0; c < 2; c++) { const d = ir.getChannelData(c); for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3.2) * (i < 90 ? i / 90 : 1); }
    revIn = AC.createConvolver(); revIn.buffer = ir; const rg = AC.createGain(); rg.gain.value = .55; revIn.connect(rg); rg.connect(master); }
  muffle = filt('lowpass', 1100, .7); muffle.connect(voiceG);
  analyser = AC.createAnalyser(); analyser.fftSize = 512; voiceG.connect(analyser); abuf = new Uint8Array(analyser.fftSize);
  bedInit();
  return (decoding = Promise.all(Object.entries(rawSnd).filter(([k]) => SNDS.includes(k)).map(([k, b]) => decodeOne(k, b))).then(() => {
    for (const [k, b] of Object.entries(rawSnd)) if (!BUF[k]) decodeOne(k, b);   // фоновые — не задерживают старт
  }));
}
function unlockAudio() {   // только внутри нажатия: иначе телефон (особенно iPhone) не даст включить звук
  if (!AC) return;
  try { AC.resume(); const b = AC.createBuffer(1, 1, 22050), s = AC.createBufferSource(); s.buffer = b; s.connect(AC.destination); s.start(0); } catch {}
}
function goFull() {   // полный экран по нажатию «Играть»: только внутри нажатия, иначе браузер откажет
  const d = document, el = d.documentElement;
  if (d.fullscreenElement || d.webkitFullscreenElement) return;
  try {
    const r = (el.requestFullscreen || el.webkitRequestFullscreen || function () {}).call(el, { navigationUI: 'hide' });
    if (r && r.then) r.then(() => { try { screen.orientation.lock('portrait').catch(() => {}); } catch {} }).catch(() => {});
  } catch {}
}
const playing = new Set();
// сколько низа каждого звука отправить в «телефонный» слой обертонов
const SEND = { heart: 2.2, heart_held: 1, sub_presence: 3, drone_dread: 1.6, boom: 1.6, metal_groan: .7 };
function play(name, { when = 0, bus = 'sfx', loop = false, gain = 1, off = 0, rate = 1, to = null, cut = 0, pan = 0, lp = 0, rev = 0 } = {}) {
  if (name === 'jumpscare' && !to && directG) to = [[directG, 1]];
  if (!AC || !BUF[name]) return null;
  const s = AC.createBufferSource(), gn = AC.createGain(); s.buffer = BUF[name]; s.loop = loop; gn.gain.value = gain; s.playbackRate.value = rate;
  let head = s;
  if (lp) { const f = filt('lowpass', lp, .7); head.connect(f); head = f; }
  if (pan && AC.createStereoPanner) { const p = AC.createStereoPanner(); p.pan.value = Math.max(-1, Math.min(1, pan)); head.connect(p); head = p; }
  head.connect(gn);
  if (to) { for (const [nd, g2] of to) { if (g2 === 1) gn.connect(nd); else { const sg = AC.createGain(); sg.gain.value = g2; gn.connect(sg); sg.connect(nd); } } }
  else gn.connect(bus === 'voice' ? voiceG : bus === 'music' ? musicG : sfxG);
  if (rev && revIn) { const rg = AC.createGain(); rg.gain.value = rev; gn.connect(rg); rg.connect(revIn); }
  if (!to && bus === 'sfx' && SEND[name] && lowSend) { const sg = AC.createGain(); sg.gain.value = SEND[name]; gn.connect(sg); sg.connect(lowSend); }
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
  curMusic = name ? play(name, { bus: 'music', loop: true, gain: .0001 }) : null;
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
let bedG = null, bed = null;
const BEDS = { lobby: { tone: .24, f: 9000, hum: .0025 }, corridor: { tone: .2, f: 6500, hum: .009 }, server: { tone: .32, f: 650, hum: .0015 } };
// дождь: снаружи — открыто; у стекла — глуше; в холле — далеко; на этажах и в подвале почти не слышно
const RAINS = { out: [.34, 9000], glass: [.2, 2400], in: [.075, 750], far: [.014, 420], none: [0, 420] };
function bedInit() {
  bedG = AC.createGain(); bedG.gain.value = 0; bedG.connect(master);
  const bs = AC.createGain(); bs.gain.value = .6; bedG.connect(bs); bs.connect(lowSend);
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
  o.connect(gn); const out = panTo(gn, pan); out.connect(sfxG); const rg = AC.createGain(); rg.gain.value = .6; out.connect(rg); rg.connect(revIn); o.start(t); o.stop(t + .15);
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
  s.connect(hp); hp.connect(pk); pk.connect(gn); gn.connect(sfxG); const rg = AC.createGain(); rg.gain.value = .25; gn.connect(rg); rg.connect(revIn);
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
document.addEventListener('visibilitychange', () => { paused = document.hidden; if (AC) document.hidden ? AC.suspend() : AC.resume(); });

/* ---------- состояние ---------- */
let mode = 'load', scene = null, sceneId = '', bi = 0, beat = null, bT0 = 0, bDur = 0, sched = [], fired = new Set(), onBeatEnd = null;
let gen = 0;   // номер текущей сцены: старые таймеры и мини-игры по нему понимают, что устарели
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
let flashT = -9, blackT = -9, scareAt = null, shakeAmp = 0, typed = null, hb = 0, pre = 0, trig = [], subl = null, sublScene = '', voiceNow = false;
let creep = null, creepCool = 25, mgView = null;   // случайное появление Жильца в кадре; кадр под мини-игрой
let sceneT0 = 0, secBase = 7, lastTime = '02:55', lastDrawn = 'door_katya', falseAlarm = false;
const flags = {};   // что уже случилось за ночь: дверь открыта, Михалыч на CAM 13 (для тихих перемен в холле)
const buzz = ms => { try { if (navigator.vibrate && !LS.get('mute', false)) navigator.vibrate(ms); } catch {} };
const seen = new Set(LS.get('seen', []));
const frozenC = document.createElement('canvas'); frozenC.width = W; frozenC.height = H; const frozenG = frozenC.getContext('2d');

function durOf(k) { return (TIM[k] && TIM[k].dur) || (BUF[k] && BUF[k].duration) || 2; }
// кто говорит: имя на табличке и цвет
const SPK = { 'ТЁМЫЧ': { c: '#cfd3da', cls: 'narr', sub: 'ты' }, 'СМЕНЩИК': { c: '#ffd27a' }, 'КАТЯ': { c: '#ff9fbc' }, 'КАТЯ?': { c: '#ff4f7e' },
  '???': { c: '#ff3030', cls: 'monster' }, 'МИХАЛЫЧ': { c: '#8fd0ff' }, 'УБОРЩИК': { c: '#9fd8a8' }, 'КАМЕРА': { c: '#7fe0a0' }, 'ТЕЛЕФОН': { c: '#9aa3b5' } };
const VOICE_WHO = { ch_guard: 'СМЕНЩИК', ch_katya: 'КАТЯ', ch_whisper: 'КАТЯ?', ch_monster: '???' };
const whoOf = k => VOICE_WHO[k] || (TIM[k] && TIM[k].who) || 'ТЁМЫЧ';
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
  const tm = lastTime < '03:00' ? lastTime : '03:00';
  return Object.assign({ clock: tm, jacket: flags.opened ? 0 : 1, badge: flags.mih ? 'scratched' : 1 }, scene && scene.props, beat && beat.props);
}
function gameClock(withSec = true) {
  const [h, m] = lastTime.split(':').map(Number), s = h * 3600 + m * 60 + secBase + Math.floor(Math.max(0, now() - sceneT0)), p2 = v => String(v).padStart(2, '0');
  return p2(Math.floor(s / 3600) % 24) + ':' + p2(Math.floor(s / 60) % 60) + (withSec ? ':' + p2(s % 60) : '');
}

function startScene(id) {
  if (!S.scenes[id]) { console.warn('нет сцены', id); return menu(); }   // сохранение из старой версии сценария не ломает игру
  gen++; stopAll(); choiceEl.classList.add('hide'); mgEl.classList.add('hide'); mgEl.innerHTML = ''; mgView = null;
  uiEl.innerHTML = ''; $('hud').classList.remove('hide');
  sceneId = id; scene = S.scenes[id]; mode = 'play'; sceneT0 = now(); secBase = scene.sec ?? 7; sublScene = '';
  if (/^(open|booth|exit|sister)/.test(id)) flags.opened = true;
  if (/^(cam13b|down|basement|knock3|hold_door|dawn|rules|call540|hangup)/.test(id)) flags.mih = true;
  if (!id.startsWith('over_')) LS.set('save', id);
  if (scene.music) music(scene.music);
  if (scene.time) { lastTime = scene.time; clockEl.textContent = 'НОЧЬ 1 · ' + scene.time; clockEl.classList.remove('hide'); }
  bi = -1; nextBeat();
}
function nextBeat() {
  bi++; amb('heart', false); holding = false; waiting = false; tapEl.classList.add('hide'); dlgEl.classList.remove('wait');
  const carry = trig.filter(e => !e.done && e.carry); trig = [];
  if (!scene.beats[bi]) { seen.add(sceneId); LS.set('seen', [...seen]); carry.forEach(e => e.fn()); return runThen(scene.then); }
  if (hushOn) hush(false);
  dropPending();
  // тот же кадр подряд — действие продолжается, а не начинается заново (никто не телепортируется назад)
  { const pv = bi > 0 && scene.beats[bi - 1], nb = scene.beats[bi], same = pv && pv.img && pv.img === nb.img && pv === beat;
    shotOff = same ? shotOff + (now() - bT0) : 0; darkHold = !!(same && pv.fx === 'dark'); }
  const prevBeat = beat;
  beat = scene.beats[bi]; bT0 = now(); fired.clear(); sched = []; typed = null; scareAt = null; hb = 0;
  if (beat.hush && AC) hush(true);
  if (beat.time) { lastTime = beat.time; secBase = 0; sceneT0 = now(); clockEl.textContent = 'НОЧЬ 1 · ' + beat.time; }   // часы перещёлкнулись посреди сцены
  chapEl.classList.toggle('hide', !beat.chapter); if (beat.chapter) chapEl.innerHTML = `<b>${beat.chapter[0]}</b><span>${beat.chapter[1]}</span>`;
  let t = .15;
  for (const k of beat.say || []) {
    sched.push({ t, k, d: durOf(k) });
    if (beat.scareGap && k === beat.say[0]) scareAt = t + gapTime(k);
    t += durOf(k) + .3;
  }
  if (beat.text) { const d = Math.max(2.2, beat.text.length / 14 + 1.3); typed = { t0: beat.say ? t : .2, d, s: beat.text }; t = Math.max(t, typed.t0 + d); }
  if (beat.scare != null) scareAt = beat.scare;
  creep = null;
  bDur = Math.max(t + (beat.pad || .3), 1.2);
  // смс на телефон: баннер сверху; кадр не кончится, пока её не дочитать
  for (const m of [].concat(beat.sms || [])) { const at = m.at ?? .5; trig.push({ at, fn: () => showSms(m) }); bDur = Math.max(bDur, at + 3.2); }
  // перед скримером — не меньше 1.2 с тишины: если удар в самом начале кадра, прошлый кадр замирает и темнеет в тишине;
  // fx 'glitch' — прошлый кадр замирает на 0.8 с с надписью «соединение потеряно», потом жёсткая склейка
  pre = beat.fx === 'glitch' ? .8 : scareAt != null && scareAt < 1.2 && beat.fx !== 'lunge' ? 1.3 - scareAt : 0;
  if (pre) { frozenG.drawImage(cv, 0, 0); bT0 = now() + pre; if (beat.fx === 'glitch') glitchStart(); }
  ruleEl.classList.toggle('hide', !beat.rule); if (beat.rule) { ruleEl.textContent = beat.rule; learnRule(beat.rule); }
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
  beatAudio();
}
function beginBeat() { waiting = false; tapEl.classList.add('hide'); chapEl.classList.add('hide'); if (beat.rule) ruleEl.classList.remove('hide'); bT0 = now() + pre; play('click'); beatAudio(); }
function beatAudio() {
  const P = pre;   // все звуки кадра сдвигаются на длину пред-кадра
  for (const [n, at, op] of beat.sfx || []) {   // [звук, секунда, {gain, cut, off, lp, pan, rate}]
    if (n === 'static') amb('static', true, .25);
    // стук по стеклу: если Катя стучит в кадре, звук даёт её рука (синхронно), а не таблица
    else if (n === 'knock' || n === 'glass_tap') trig.push({ at, fn: () => { if (!(beat.img && lastDrawn === beat.img && katyaKnocking())) { play(n); if (n === 'knock') buzz(40); } } });
    else { play(n, Object.assign({ when: at + P }, op)); if (n === 'door') setTimeout(() => buzz(40), (at + P) * 1000); }
  }
  for (const it of sched) for (const vo of voiceFx(it.k)) { const o = play(it.k, Object.assign({ bus: 'voice' }, vo, { when: it.t + P + (vo.dt || 0) })); if (o) o.voice = true; }
  if (beat.fx !== 'static' && !(beat.sfx || []).some(s => s[0] === 'static')) amb('static', beat.fx === 'static', .3);
  if (scareAt != null) {
    const sa = scareAt + P;
    // не «бум» из ужастика для детей: нарастание обрывается в тишину, а потом — близкий сухой звук рядом с ухом
    if (beat.fx === 'lunge') { play('breath_close', { when: sa - 1.0, gain: .9 }); play(BUF.jumpscare ? 'jumpscare' : 'scare', { when: sa, gain: calm() ? .8 : .95 }); }
    else if (sa > 1.6 && BUF.reverse_swell) play('reverse_swell', { when: sa - BUF.reverse_swell.duration, gain: .5 });
    const hits = beat.hit || (sceneId.startsWith('over_') ? ['neck_crack', 'breath_close'] : ['breath_close']);
    if (beat.fx !== 'lunge') hits.forEach((h, i) => play(BUF[h] ? h : 'scare', { when: sa + i * .12, gain: i ? .7 : 1 }));
    if (hits.includes('glass_slam')) slamHitT = now() + sa;
    if (BUF.sub_presence) play('sub_presence', { when: sa, gain: .8, cut: 2.5 });
  }
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
  if (t < 0) return;                                         // тишина перед ударом и «соединение потеряно» не пропускаются
  if (scareAt != null && t < scareAt + 1) return;            // скример не пропускается
  for (const o of [...playing]) if (o.voice) { try { o.s.stop(); } catch {} playing.delete(o); }
  dropPending();
  for (const e of trig) if (!e.done && !e.carry && e.at > t) e.done = true;   // отложенные звуки кадра больше не нужны (смс покажем сразу)
  for (const m of [].concat(beat.sms || [])) if ((m.at ?? .5) > t) showSms(m);
  bT0 = now() - bDur;                                        // сразу к концу кадра (реплика показана целиком)
}
const known = new Set(LS.get('rules', []));
function learnRule(r) { if (/^\d\./.test(r) || r.startsWith('«')) { known.add(r); LS.set('rules', [...known]); } }
function showRules() {
  const list = [...known].sort();
  noteEl.innerHTML = `<div>${list.length ? list.filter(r => /^\d/.test(r)).join('\n\n') + list.filter(r => !/^\d/.test(r)).map(r => `<i>${r}</i>`).join('') : 'Правил пока нет.'}<small>нажми, чтобы закрыть</small></div>`;
  noteEl.classList.remove('hide'); noteEl.onclick = () => noteEl.classList.add('hide');
}

/* ---------- смс: уведомление на экране блокировки, сверху ---------- */
let smsTimer = 0;
function showSms(m) {
  if (!smsEl || !m) return;
  smsEl.innerHTML = `<div class="si">${ICON.msg}</div><div class="sb"><div class="sh"><span>СООБЩЕНИЯ</span><span>сейчас</span></div><b>${escH(m.from || 'Катя')}</b><p>${escH(m.text || '')}</p></div>`;
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
  dlgEl.classList.add('hide'); tapEl.classList.add('hide'); chapEl.classList.add('hide'); ruleEl.classList.add('hide'); amb('static', false); amb('heart', false);
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
        : `<div class="cam ${seenCams.has(i) ? 'seen' : ''}" data-i="${i}" style="background-image:url(${window.World ? World.thumb(c.img, { cx: c.c[0], cy: c.c[1], z: c.c[2], cctv: 1 }) : ''})"><b>${c.id} ${c.name}</b></div>`).join('') + '</div>' +
      `<div class="mgfoot">${seenCams.size >= 3 ? '<button class="btn" id="cgo" style="text-align:center">Продолжить смену</button>' : `<div style="text-align:center;opacity:.7;font-size:3.6cqw">Проверено: ${seenCams.size} из 3</div>`}</div>`;
    mgEl.querySelectorAll('.cam').forEach(el => el.onclick = () => {
      const i = +el.dataset.i, c = S.cams[i];
      if (c.forbidden) {
        if (!warned) { warned = true; play('static'); if (!calm()) { mgEl.classList.add('flash'); setTimeout(() => mgEl.classList.remove('flash'), 300); }
          el.innerHTML = '<b>CAM 13</b><span style="font-size:3.6cqw">ТОЧНО ВКЛЮЧИТЬ?<br>ПРАВИЛО 4</span>'; return; }
        amb('static', false); mgEl.classList.add('hide'); return startScene('over_cam13');
      }
      play('click'); play('static', { gain: .5 });
      camView = { c, t0: now() }; mgEl.classList.add('hide');
      lastSubKey = ''; say('КАМЕРА', c.note, c.note.length);
      if (c.jump && !c.jumped) { c.jumped = true; camView.jump = true; hush(true, true);
        setTimeout(() => { if (my !== gen) return; hush(false); play(BUF.jumpscare ? 'jumpscare' : 'scare', { gain: .8 }); buzz([200, 40, 300]); shakeAmp = 40; }, 1250); }
      setTimeout(() => { if (my !== gen) return; camView = null; dlgEl.classList.add('hide'); seenCams.add(i); if (c.creep) play('boom', { gain: .4 }); mgEl.classList.remove('hide'); draw(); }, camView.jump ? 3300 : 2600);
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
    const btn = $('hb'), air = $('air'); let held = false, a = 1, t0 = now(), lt = t0, safe = false, end = false, steps = 0;
    const dur = th.dur || 9, set = h => { held = h; btn.classList.toggle('held', h); btn.textContent = h ? 'НЕ ДЫШИ' : 'ДЕРЖИ'; };
    btn.onpointerdown = e => { e.preventDefault(); set(true); }; btn.onpointerup = btn.onpointerleave = btn.onpointercancel = () => set(false);
    const fin = ok => { if (end || my !== gen) return; end = true; amb('heart', false); mgEl.classList.add('hide'); startScene(ok ? th.win : th.lose); };
    (function loop() {
      if (end || my !== gen) return; const t = now() - t0, fdt = Math.min(.1, now() - lt); lt = now();
      // босые шаги кружат вокруг будки: то слева, то справа
      if (t > .8 && Math.floor(t / 1.1) > steps && t < dur) { steps++; const pan = Math.sin(t * 1.3) * .8; if (!play('steps_barefoot', { off: Math.random() * 4, cut: 1, gain: .6 + .4 * Math.sin(t), pan })) play('steps', { gain: .5 + .5 * Math.sin(t), pan }); if (steps % 3 === 0) play(BUF.breath_close ? 'breath_close' : 'ch_whisper', { gain: .45, pan: -pan * .6 }); }
      a = Math.max(0, Math.min(1, a + (held ? -0.085 : 0.25) * fdt)); air.style.width = a * 100 + '%';
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
    mgEl.innerHTML = `<div class="mghead low">${th.title || 'ВЫДЕРНИ ШНУР'}<small>${th.hint || 'Тяни вилку вниз, быстро!'}</small></div><div class="cord"><div class="wire"></div><div class="plug" id="plug">${ICON.plug}</div></div><div class="mgfoot" style="bottom:8%"><div class="meter"><i id="ct" style="width:100%"></i></div></div>`;
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
      mgEl.innerHTML = `<div class="mghead">${th.title}<small>Свет гаснет ${bad === 'L' ? 'СЛЕВА' : 'СПРАВА'} — беги ${bad === 'L' ? 'ВПРАВО →' : '← ВЛЕВО'} (${k + 1}/${N})</small></div><div class="mgfoot" style="bottom:72%"><div class="meter"><i id="rt" style="width:100%"></i></div></div><div class="arrows"><button data-d="L" aria-label="Влево">${ICON.left}</button><button data-d="R" aria-label="Вправо">${ICON.right}</button></div>`;
      mgEl.classList.remove('hide'); play('steps', { gain: .8 }); shakeAmp = 14;
      const t0 = now(), lim = Math.max(1.3, 2.6 - k * .2); let ok = false;
      mgEl.querySelectorAll('.arrows button').forEach(b => b.onclick = () => { if (ok) return; ok = true; if (b.dataset.d === bad) return fin(false); play('click'); k++; step(); });
      (function loop() { if (end || ok || my !== gen) return; const l = lim - (now() - t0); const el = $('rt'); if (el) el.style.width = Math.max(0, l / lim * 100) + '%'; if (l <= 0) return fin(false); requestAnimationFrame(loop); })();
    };
    step();
  },
  // запотевшее стекло: протереть пальцем; когда видно больше половины — в стекло бьёт ладонь
  wipe(th) {
    mode = 'mg'; music('tension'); amb('heart', true, .35); const my = gen, t0 = now(), FW = 108, FH = 192;
    const F = document.createElement('canvas'); F.width = FW; F.height = FH; const f = F.getContext('2d');
    // пелена гуще к краям, в ней капельки; несколько подтёков — по ним чуть видно, что за стеклом
    const rg = f.createRadialGradient(FW / 2, FH * .45, 10, FW / 2, FH * .45, FH * .62); rg.addColorStop(0, 'rgba(150,162,172,.84)'); rg.addColorStop(1, 'rgba(120,130,140,.95)'); f.fillStyle = rg; f.fillRect(0, 0, FW, FH);
    for (let i = 0; i < 260; i++) { f.fillStyle = `rgba(${200 + Math.random() * 50},${210 + Math.random() * 40},230,${.08 + Math.random() * .16})`; f.beginPath(); f.arc(Math.random() * FW, Math.random() * FH, .4 + Math.random() * 1.3, 0, 7); f.fill(); }
    f.globalCompositeOperation = 'destination-out';
    for (let i = 0; i < 7; i++) { let x = Math.random() * FW, y = Math.random() * 60; const n = 30 + Math.random() * 60; f.fillStyle = 'rgba(0,0,0,.45)';
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
    let end = false, seen = 0, onMon = 0, onGoal = 0, gone = false, found = 0, jump = 0, hitT = 0, lt = now();
    const pt = e => { const r = cv.getBoundingClientRect(); tgt.x = clamp((e.clientX - r.left) / r.width, 0, 1); tgt.y = clamp((e.clientY - r.top) / r.height - .07, 0, 1); };   // луч чуть выше пальца — палец не закрывает
    mgEl.onpointerdown = e => { e.preventDefault(); if (!found) pt(e); }; mgEl.onpointermove = e => { if (!found && (e.buttons || e.pointerType === 'touch')) pt(e); };
    const near = (p, r) => Math.hypot((L.x - p[0]) * .5625, L.y - p[1]) < r;   // расстояние в долях высоты экрана
    const fin = () => { if (end || my !== gen) return; end = true; amb('heart', false); mgEl.onpointerdown = mgEl.onpointermove = null; mgEl.classList.add('hide'); mgView = null; startScene(th.win); };
    (function loop() {
      if (end || my !== gen) return; const dt = Math.min(.1, now() - lt); lt = now();
      const kf = Math.min(1, dt * (jump ? 22 : 9)); L.x += (tgt.x - L.x) * kf; L.y += (tgt.y - L.y) * kf;   // фонарь тяжёлый — луч догоняет палец
      if (!seen) { onMon = near(th.mon, .06) ? onMon + dt : 0;
        if (onMon > .25) { seen = now(); play('breath_close', { gain: .9 }); play('whispers', { gain: .35, cut: 1.4 }); shakeAmp = 10; buzz([40, 30, 80]); } }
      else if (!gone && now() - seen > .75) { gone = true; mgView.img = th.img2; blackT = now(); play('fluor_flicker', { gain: .4, cut: .3 }); $('th').textContent = th.hint2; }
      if (gone && !found) { onGoal = near(th.goal, .05) ? onGoal + dt : 0; if (onGoal > .7) { found = now(); play('click'); $('th').textContent = th.found; } }
      if (found && th.img3) { const e = now() - found;   // за спиной вздох — луч сам дёргается влево, а он стоит вплотную
        if (e > .9 && !jump) { jump = now(); mgView.img = th.img3; mgView.st = -(jump - mgView.t0); tgt.x = th.near[0]; tgt.y = th.near[1]; play('breath_close', { gain: 1 }); mgEl.querySelector('.mghead').classList.add('hide'); }
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
    mgView = { img: th.img, c: th.cam || [.47, .56, 1.9, 1.9], t0: now(), dur: 1e9, tAt: len, label: th.label, rw: 0, rwHud: { tag: '◀◀ ЗАПИСЬ', clock: clockAt(len) } };
    mgEl.innerHTML = `<div class="mghead cam">${th.title}<small id="th">${th.hint}</small></div><div class="tape" id="tape"><div class="tbar"><i id="tf"></i></div><div class="tknob" id="tk">${ICON.left}</div></div>`;
    mgEl.classList.remove('hide'); play('click');
    const box = $('tape'), knob = $('tk'), fill = $('tf'); let drag = false, end = false, lastTick = len, found = 0;
    const setK = k => { k = clamp(k, 0, 1); const tt = k * len; mgView.rw = Math.min(1, mgView.rw + Math.abs(tt - mgView.tAt) * 1.5); mgView.tAt = tt; mgView.rwHud.clock = clockAt(tt);
      knob.style.left = k * 100 + '%'; fill.style.width = k * 100 + '%';
      if (Math.abs(tt - lastTick) > .18) { lastTick = tt; play('tick', { gain: .16, rate: 1.5 }); }
      if (!found && tt < th.goal) { found = now(); drag = false; box.classList.add('done'); play('click'); $('th').textContent = th.found; mgView.rwHud.tag = '▶ ЗАПИСЬ'; } };
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
function gallery() {
  const got = LS.get('endings', []).filter(k => S.endings[k]), ov = LS.get('overs', []).filter(k => S.overs.includes(k));
  return `<div class="gal">${Object.entries(S.endings).map(([k, n]) => `<div class="${got.includes(k) ? 'got' : ''}">${got.includes(k) ? n : '???'}</div>`).join('')}</div>
    <small>Концовки: ${got.length} из ${Object.keys(S.endings).length} · Смертей найдено: ${ov.length} из ${S.overs.length}</small>`;
}
/* ---------- спонсоры игры (донаты звёздами Telegram через бота) ---------- */
const API = /railway\.app$|^localhost$/.test(location.hostname) ? '' : 'https://office-dreams-production.up.railway.app';
let SP = { donate: 'https://t.me/officedream1_bot?start=games', list: [] };
get(API + '/api/games/sponsors', 'json').then(j => { if (j && j.ok) SP = Object.assign(SP, j, { donate: j.donate || SP.donate }); }).catch(() => {});
function sponsorsHtml() {
  const names = SP.list.slice(0, 30).map(r => `<span>${escH(r.name)}</span>`).join('');
  return `<div class="spons"><b>СПОНСОРЫ ИГРЫ</b>${names || '<i>Стань первым — твоё имя будет здесь</i>'}</div>`;
}
const supportBtn = (cls = 'btn ghost sup') => `<a class="${cls}" href="${escH(SP.donate)}" target="_blank" rel="noopener">${ICON.star}<span>Поддержать игру</span></a>`;
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
function menu() {
  gen++; mode = 'menu'; menuT0 = now(); titleT0 = now(); stopAll(false); music(null); for (const k in ambient) amb(k, false); if (hushOn) hush(false); $('hud').classList.add('hide'); hideSms();
  choiceEl.classList.add('hide'); mgEl.classList.add('hide'); dlgEl.classList.add('hide'); tapEl.classList.add('hide'); chapEl.classList.add('hide'); clockEl.classList.add('hide'); noteEl.classList.add('hide'); ruleEl.classList.add('hide'); waiting = false; beat = null;
  const sv = LS.get('save', null), save = sv && S.scenes[sv] ? sv : null;   // сохранение из старой версии сценария не ломает меню
  uiEl.innerHTML = `<div class="title ${gateDone ? 'open' : 'gate'}${titleFirst ? ' first' : ''}">
    <div class="logo" id="logo"><b>NIGHT</b><b>SHIFT</b></div>
    <div class="tagl">Четыре правила · Одна ночь · Сестра за стеклом</div>
    <div class="tts">TAP TO START</div>
    <nav class="mlist">
      ${save ? '<button id="mCont">Продолжить</button>' : ''}
      <button id="mNew">${save ? 'Начать заново' : 'Начать смену'}</button>
      <button id="mGal">Концовки</button>
      <button id="mSet">Настройки</button>
      ${supportBtn('msup')}
    </nav>
    <div class="hp">${ICON.phones}<span>Лучше в наушниках · в игре есть вспышки света</span></div><div class="hp"><b style="color:#e33;margin-right:6px">18+</b><span>Жестокость, кровь и сцены насилия</span></div></div>`;
  const lg = $('logo'), m = `url(${logoMask()})`; lg.style.webkitMaskImage = lg.style.maskImage = m; lg.style.webkitMaskSize = lg.style.maskSize = '100% 100%';
  const T0 = uiEl.querySelector('.title'), ok = () => gateDone && now() - gateT > .45;   // тот же тап, что открыл меню, не жмёт кнопку под пальцем
  const btn = (id, fn) => { const b = $(id); if (b) b.onclick = e => { e.stopPropagation(); if (!ok()) return; uiSnd(); fn(); }; };
  btn('mNew', () => begin('intro')); btn('mCont', () => begin(save)); btn('mSet', settings);
  btn('mGal', () => { subScreen(`<h1>КОНЦОВКИ</h1>${gallery()}`); });
  const sup = T0.querySelector('.msup'); if (sup) sup.onclick = e => { if (!ok()) e.preventDefault(); };
  if (!gateDone) { T0.onpointerdown = e => { if (e.target.closest('button, a')) return; openGate(); }; if (navigator.webdriver) setTimeout(openGate, 1200); }   // автотесты не умеют «тапнуть, чтобы начать»
  else titleAudio();
  // мигание логотипа (больная лампа) — со звуком, если звук уже разрешён
  setTimeout(() => { if (mode === 'menu' && AC && AC.state === 'running') { play('fluor_flicker', { off: 1.45, cut: .3, gain: .16 }); tubeBuzz(.06); } }, titleFirst ? 2380 : 2380);
  titleFirst = false;
}
function openGate() {
  if (gateDone) return; gateDone = true; gateT = now(); unlockAudio(); initAudio();
  const T0 = uiEl.querySelector('.title'); if (T0) { T0.classList.remove('gate'); T0.classList.add('open'); T0.onpointerdown = null; }
  uiSnd('open'); titleAudio();
}
function titleAudio() { bedSet('lobby', 'glass', .9); ambNext = now() + 4; }
function subScreen(html) {   // концовки / настройки поверх титульного кадра
  uiEl.innerHTML = `<div class="screen sub">${html}<button class="btn ghost" id="mBack">Назад</button></div>`;
  $('mBack').onclick = () => { uiSnd('back'); menu(); };
}
const VHS_LV = [0, .32, .7], VHS_N = ['ВЫКЛ', 'СЛАБО', 'ОБЫЧНО'];
function settings() {
  const row = (id, name, val) => `<button class="set" id="${id}"><span>${name}</span><b>${val}</b></button>`;
  const vh = LS.get('vhs', 2);
  subScreen(`<h1>НАСТРОЙКИ</h1><div class="sets">
    ${row('sMute', 'Звук', LS.get('mute', false) ? 'ВЫКЛ' : 'ВКЛ')}
    ${row('sVhs', 'Плёнка VHS', VHS_N[vh] || VHS_N[2])}
    ${row('sCalm', 'Меньше вспышек', calm() ? 'ВКЛ' : 'ВЫКЛ')}
    ${row('sAuto', 'Реплики сами', auto ? 'ВКЛ' : 'ВЫКЛ')}</div>
    <small>«Меньше вспышек» убирает кадры-вспышки и смягчает резкие засветы.</small>`);
  const re = () => { uiSnd(); settings(); };
  $('sMute').onclick = () => { setMute(!LS.get('mute', false)); re(); };
  $('sVhs').onclick = () => { const v = (LS.get('vhs', 2) + 2) % 3; LS.set('vhs', v); if (window.World) World.vhs = VHS_LV[v]; re(); };
  $('sCalm').onclick = () => { LS.set('calm', !calm()); re(); };
  $('sAuto').onclick = () => { auto = !auto; LS.set('auto', auto); $('bAuto').classList.toggle('on', auto); re(); };
}
function setMute(m) { LS.set('mute', m); if (master) master.gain.value = m ? 0 : 1; if (directG) directG.gain.value = m ? 0 : 1; $('bMute').classList.toggle('muted', m); }
async function begin(id) {
  unlockAudio(); goFull();   // до любого await, пока идёт нажатие
  if (id === 'intro') { for (const k in flags) delete flags[k]; falseAlarm = false; lastTime = '02:55'; }
  uiEl.innerHTML = `<div class="screen"><small>Загрузка звука…</small></div>`;
  await Promise.race([initAudio(), new Promise(r => setTimeout(r, 8000))]);   // звук не успел — играем дальше, без зависания
  uiEl.innerHTML = ''; startScene(id);
}
function endScreen(th) {
  clockEl.classList.add('hide'); $('hud').classList.add('hide'); hideSms();
  mode = 'end'; const got = new Set(LS.get('endings', [])); got.add(th.ending); LS.set('endings', [...got]); LS.set('save', null);
  play('boom'); music('drone');
  uiEl.innerHTML = `<div class="screen"><h1>${th.title}<em>${S.endings[th.ending].toUpperCase()}</em></h1><p>${th.sub}</p>${gallery()}
    <button class="btn" id="eAgain" style="margin-top:5cqw">Сыграть ещё раз</button><button class="btn ghost" id="eMenu">Меню</button>${supportBtn()}${sponsorsHtml()}</div>`;
  $('eAgain').onclick = () => { goFull(); uiEl.innerHTML = ''; for (const k in flags) delete flags[k]; falseAlarm = false; startScene('intro'); }; $('eMenu').onclick = menu;
}
function overScreen(th) {
  clockEl.classList.add('hide'); $('hud').classList.add('hide'); hideSms();
  mode = 'end'; const ov = new Set(LS.get('overs', [])); ov.add(th.over); LS.set('overs', [...ov]);
  music(null);
  uiEl.innerHTML = `<div class="screen over"><h1>${th.over}</h1><p>${th.sub}<br>Ты не дожил до утра.</p>
    <button class="btn" id="oRetry">Попробовать снова</button><button class="btn ghost" id="oMenu">Меню</button>
    <small>Смертей найдено: ${[...ov].filter(k => S.overs.includes(k)).length} из ${S.overs.length}</small></div>`;
  $('oRetry').onclick = () => { goFull(); uiEl.innerHTML = ''; startScene(th.retry); }; $('oMenu').onclick = menu;
}

/* ---------- рисование ---------- */
const noise = document.createElement('canvas'); noise.width = noise.height = 256;
{ const n = noise.getContext('2d'), d = n.createImageData(256, 256); for (let i = 0; i < d.data.length; i += 4) { const v = Math.random() * 255; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; } n.putImageData(d, 0, 0); }
let noisePat = null;
const drops = Array.from({ length: 140 }, () => ({ x: Math.random() * W, y: Math.random() * H, v: 1800 + Math.random() * 1400, l: 40 + Math.random() * 70 }));
// кадр камеры наблюдения обновляется рывками — 12 кадров в секунду, как настоящий регистратор: между кадрами что-то успевает сдвинуться
const heldC = document.createElement('canvas'); heldC.width = W; heldC.height = H; const heldG = heldC.getContext('2d'); let heldT = -9, heldK = '';
function heldShot(id, o, T, key) {
  if (T - heldT > 1 / 12 || key !== heldK || T < heldT) { heldT = T; heldK = key; shot(id, o, heldG); }
  g.drawImage(heldC, 0, 0);
}
// кадр 3D-мира; без WebGL — чёрный экран с подсказкой
let worldTried = false, menuT0 = 0, shotOff = 0, darkHold = false;
function shot(id, o, cx = g) {
  if (window.World && World.ok && World.draw(cx, W, H, id, Object.assign(o, { props: propsNow() }))) { lastDrawn = id; return true; }
  cx.fillStyle = '#000'; cx.fillRect(0, 0, W, H); if (!worldTried) return false;   // пока мир грузится — просто чёрный
  g.fillStyle = '#777'; g.font = '700 40px Inter, Arial'; g.textAlign = 'center'; g.fillText('Нужен браузер с WebGL', W / 2, H * .3); g.textAlign = 'left';
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
function cctvHud(label, T, rw) {   // rw = { tag, clock } — запись на перемотке
  if (T > tearNext) { tearNext = T + 2.2 + Math.random() * 4.5; tear(1); }
  grain(.12);
  g.save(); g.textBaseline = 'top'; g.shadowColor = 'rgba(0,0,0,.9)'; g.shadowBlur = 6;
  g.strokeStyle = 'rgba(235,240,235,.55)'; g.lineWidth = 4; const m = 46, L = 64;
  g.beginPath(); for (const [x, y, sx, sy] of [[m, m, 1, 1], [W - m, m, -1, 1], [m, H - m, 1, -1], [W - m, H - m, -1, -1]]) { g.moveTo(x + sx * L, y); g.lineTo(x, y); g.lineTo(x, y + sy * L); } g.stroke();
  g.font = '700 40px "Courier New", monospace'; g.fillStyle = 'rgba(235,240,235,.9)';
  if ((T % 1) < .5) { g.fillStyle = '#e8262e'; g.beginPath(); g.arc(98, 222, 13, 0, 7); g.fill(); g.fillStyle = 'rgba(235,240,235,.9)'; }
  g.fillText(rw ? rw.tag : 'REC', 124, 204);
  if (label) g.fillText(label, 84, 258);
  g.font = '700 34px "Courier New", monospace'; g.fillText('17.10.2026  ' + (rw ? rw.clock : gameClock()), 84, 310);
  g.restore();
}
function grain(a) { g.save(); g.globalAlpha = a; g.globalCompositeOperation = 'overlay'; const ox = Math.random() * 256, oy = Math.random() * 256; g.translate(-ox, -oy); g.fillStyle = noisePat || (noisePat = g.createPattern(noise, 'repeat')); g.fillRect(0, 0, W + 256, H + 256); g.restore(); }
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
// пред-кадр: застывший прошлый кадр; перед скримером он тонет в темноте, у fx 'glitch' — окно «соединение потеряно»
function preFrame(t, T) {
  const k = 1 + t / pre;   // 0 → 1
  g.drawImage(frozenC, 0, 0);
  if (beat.fx !== 'glitch') { g.fillStyle = `rgba(0,0,0,${Math.min(.93, k * 1.15)})`; g.fillRect(0, 0, W, H); return; }
  g.fillStyle = 'rgba(0,0,0,.42)'; g.fillRect(0, 0, W, H);
  if (k < .12) tear(3);
  const bw = 820, bh = 290, bx = (W - bw) / 2, by = H * .38;
  g.fillStyle = 'rgba(12,14,16,.92)'; g.fillRect(bx, by, bw, bh); g.fillStyle = '#c8202a'; g.fillRect(bx, by, bw, 10);
  g.strokeStyle = 'rgba(230,226,214,.35)'; g.lineWidth = 2; g.strokeRect(bx + 1, by + 1, bw - 2, bh - 2);
  const cx = bx + 110, cy = by + bh / 2 + 5, st = Math.floor(T * 12) % 8;   // крутилка ожидания
  for (let i = 0; i < 8; i++) { const a = i / 8 * Math.PI * 2; g.fillStyle = `rgba(230,226,214,${i === st ? .95 : .22})`; g.fillRect(cx + Math.cos(a) * 34 - 6, cy + Math.sin(a) * 34 - 6, 12, 12); }
  g.textBaseline = 'middle'; g.fillStyle = '#e6e2d6'; g.font = '700 46px "Courier New", monospace'; g.fillText('СОЕДИНЕНИЕ ПОТЕРЯНО', bx + 190, by + 118);
  g.font = '600 32px "Courier New", monospace'; g.fillStyle = 'rgba(230,226,214,.62)'; g.fillText('переподключение' + '...'.slice(0, 1 + Math.floor(T * 4) % 3), bx + 190, by + 178);
  g.textBaseline = 'alphabetic';
}
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
  // слово полного текста = столько же слов, сколько в нём частей без знаков («05:40» — две); тире прилипает к соседнему куску
  const toks = ((TIM[k] || {}).t || '').split(/\s+/).filter(Boolean).map(s => [s, s.replace(/[.,:—?!…«»]/g, ' ').split(/\s+/).filter(Boolean).length]);
  let j = 0, debt = 0;
  const out = w.map(x => { let n = x[1].split(/\s+/).length - debt; const part = []; debt = 0;
    while (j < toks.length && (n > 0 || toks[j][1] === 0)) { part.push(toks[j][0]); n -= toks[j][1]; j++; }
    if (n < 0) debt = -n; return part.join(' ') || ''; });
  return (chunkCache[k] = j === toks.length ? out.filter(Boolean).length ? out : w.map(x => x[1]) : w.map(x => x[1]));
}
function sayWords(who, words, on) {   // озвученная реплика целиком, произнесённые слова ярче
  const key = who + '|' + on + '|' + words.join(' '); if (key === lastSubKey) return; lastSubKey = key;
  plate(who); dlgEl.querySelector('.ln').innerHTML = words.map((w, i) => `<span class="${i < on ? 'on' : ''}">${w}</span>`).join(' ');
}
let camHud = false;
function setCamHud(on) { if (on !== camHud) { camHud = on; clockEl.classList.toggle('dim', on); } }   // на экране камеры время показывает сама камера
function frame(tp) {
  const dt = Math.min(.1, (tp - lastP) / 1000); lastP = tp; if (!paused) clockT += dt;
  const T = now();
  let place = null, quiet = false, cam = false; voiceNow = false;
  if (mode === 'play' && beat) {
    let t = waiting ? 0 : T - bT0;
    if (t >= bDur) {
      const silent = !(beat.say || []).length && !beat.text;
      if (auto || silent || beat.auto) { nextBeat(); return requestAnimationFrame(frame); }
      if (!holding) { holding = true; dlgEl.classList.add('wait'); }
      t = bDur;
    }
    const p = Math.max(0, Math.min(1, t / bDur));
    // тишина перед скримером: за 1.5 с до удара фон и музыка уходят почти в ноль (у кадра с явной тишиной — своя)
    if (scareAt != null && !beat.hush) { const h = t > scareAt - 1.5 && t < scareAt; if (h !== hushOn) hush(h, true); }
    if (pre && t >= 0 && beat._preDone !== bT0) { beat._preDone = bT0; if (beat.fx === 'glitch') glitchEnd(); blackT = T - .02; }
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
        if (cur && t < cur.t + cur.d) talk = { who: whoOf(cur.k), v: talkV };
        else if (typed && typed.s && t >= typed.t0 && t < typed.t0 + typed.d - .9) talk = { who: beat.who || 'ТЁМЫЧ', v: .25 + .55 * Math.abs(Math.sin(t * 11)) };
      }
      // скример: вспышка, рывок, сильная тряска
      const sc = scareAt != null && t >= scareAt, st = sc ? t - scareAt : 0;
      const lg = beat.fx === 'lunge';
      if (sc && st < .05) { if (blackT !== T && T - blackT > .5) buzz(lg ? [250, 40, 350] : [90, 30, 160]); if (!lg) blackT = T; shakeAmp = lg ? (calm() ? 30 : 46) : 22; }
      if (lg && sc && st < .7) shakeAmp = Math.max(shakeAmp, (calm() ? 20 : 34) * (1 - st / .7));
      const sx = (Math.sin(T * 1.6) * 8 + Math.sin(T * 4.9) * 3) + (Math.random() - .5) * shakeAmp, sy = (Math.cos(T * 1.2) * 8 + Math.sin(T * 4.1) * 3) + (Math.random() - .5) * shakeAmp;
      shakeAmp *= .9;
      if (beat.phone) phone(beat.phone, t);
      else if (beat.img) {
        const cams = beat.cams || [[.5, .5, 1, 1.1]], n = cams.length, ci = Math.min(n - 1, Math.floor(p * n)), lp = p * n - ci, c = cams[ci];
        let z = c[2] + (c[3] - c[2]) * ease(lp); if (sc && beat.fx !== 'lunge') z = Math.max(z, c[3]) * (1.1 + .04 * Math.exp(-st * 6));
        const cr = creep && t >= creep.at && t < creep.at + creep.len ? creep.seed : 0;
        if (cr && !creep.hit) { creep.hit = true; if (Math.random() < .35) play('whispers', { gain: .18 }); }   // молча: заметит не каждый — так страшнее
        cam = beat.fx === 'cctv' || beat.fx === 'static' || (beat.fx === 'glitch' && !!beat.label);
        const so = { cx: c[0], cy: c[1], z, t: t + shotOff, T, sx, sy, talk, creep: cr, scare: sc ? Math.exp(-st * 2.2) : 0 };
        if (cam) so.cctv = 1;
        if (cam && beat.fx !== 'static') heldShot(beat.img, so, T, beat.img + ci); else shot(beat.img, so);   // камера наблюдения — 12 кадров в секунду
        knockSync(T);
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
        if (beat.floors) { const f = Math.min(7, 1 + Math.floor(t / .6)); g.fillStyle = 'rgba(0,0,0,.6)'; rr(W / 2 - 90, 220, 180, 150, 20); g.fillStyle = '#f33'; g.font = '800 110px Inter, Arial'; g.textAlign = 'center'; g.fillText(f, W / 2, 245); g.textAlign = 'left'; }
        if (!beat.fx || beat.fx === 'rain') grain(.07);
        if (sc && st < .6) grain(.5 * (1 - st / .6));
        // бросок: двоится от рывка, потом обрыв в черноту (крик ещё звучит)
        if (lg && sc && st < .8 && !calm()) { g.save(); g.globalAlpha = .35 * (1 - st / .8); g.globalCompositeOperation = 'lighter'; g.drawImage(cv, 16 + Math.random() * 20, 0); g.drawImage(cv, -16 - Math.random() * 20, 6); g.restore(); }
        if (lg && sc && st > .8) { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); grain(.2); }
        vignette();
        if (cam) cctvHud(beat.label, T);
      }
      // реплики: табличка с именем того, кто говорит; слова загораются по мере звучания
      let shown = false;
      if (!waiting) {
        if (typed && t >= typed.t0 && typed.s) {
          const k = Math.floor(Math.min(1, (t - typed.t0) / (typed.d - .9)) * typed.s.length);
          say(beat.who || 'ТЁМЫЧ', typed.s, k); shown = true;
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
    duck(voiceNow);
  } else if (mode === 'cams' && camView) {
    const t = T - camView.t0, c = camView.c; g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    const jt = camView.jump ? t - 1.25 : -1, sx = (Math.random() - .5) * shakeAmp, sy = (Math.random() - .5) * shakeAmp; shakeAmp *= .9;
    if (jt >= 0 && jt < .75) shot(c.jump, { cx: .5, cy: .5, z: 1, t: jt, T, sx, sy, cctv: 1, scare: Math.exp(-jt * 2.2) });
    else heldShot(c.img, { cx: c.c[0], cy: c.c[1], z: c.c[2] + t * .04, t, T, sx, sy, cctv: 1 }, T, 'cam' + c.id);
    vignette(); cctvHud(c.id + '  ' + c.name, T); if (jt >= .75) { grain(.9); if (!calm()) { g.fillStyle = 'rgba(255,255,255,.08)'; g.fillRect(0, 0, W, H); } }
    place = placeOf(c.img); cam = true;
  } else if (mode === 'mg' && mgView) {
    const t = T - mgView.t0, k = Math.min(1, t / (mgView.dur || 3)); g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    shot(mgView.img, { cx: mgView.c[0], cy: mgView.c[1], z: mgView.c[2] + (mgView.c[3] - mgView.c[2]) * k * k, t: mgView.tAt != null ? mgView.tAt : mgView.st ? mgView.st + t : t, T, sx: (Math.random() - .5) * shakeAmp, sy: (Math.random() - .5) * shakeAmp, cctv: mgView.label ? 1 : undefined, torch: mgView.torch });
    if (mgView.fog) { g.imageSmoothingEnabled = true; g.drawImage(mgView.fog, 0, 0, W, H); }
    // перемотка: дрожит и рвётся строками, пока тянут ползунок
    if (mgView.rw > .02) { const n = 2 + Math.floor(mgView.rw * 5); for (let i = 0; i < n; i++) { const y = Math.random() * H, h = 6 + Math.random() * 30 * mgView.rw; g.drawImage(cv, 0, y, W, h, (Math.random() - .5) * 90 * mgView.rw, y, W, h); g.fillStyle = `rgba(255,255,255,${.06 + .12 * Math.random() * mgView.rw})`; g.fillRect(0, y, W, 2 + Math.random() * 4); } mgView.rw *= .88; }
    if (mgView.run) { const L = mgView.run === 'L', gr = g.createLinearGradient(L ? 0 : W, 0, W / 2, 0); gr.addColorStop(0, 'rgba(0,0,0,.97)'); gr.addColorStop(.7, 'rgba(0,0,0,.75)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, W, H); }
    shakeAmp *= .9; vignette(); if (mgView.label) { cctvHud(mgView.label, T, mgView.rwHud); if (t > .3) grain(mgView.tAt != null ? .2 : .25 + k * .5); }
    place = placeOf(mgView.img); quiet = true; cam = !!mgView.label;
  } else if (mode === 'menu') {
    // титул: живой кадр, медленный наезд (20 с), сверху темнее — там логотип, снизу — меню
    const tt = T - titleT0, z = 1 + .06 * ease(Math.min(1, tt / 20));
    shot(TITLE_SHOT, { cx: .5, cy: .47, z, t: tt + 3, T, sx: 0, sy: 0 }); knockSync(T);
    let gr = g.createLinearGradient(0, 0, 0, H * .5); gr.addColorStop(0, 'rgba(0,0,0,.88)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, W, H * .5);
    gr = g.createLinearGradient(0, H * .55, 0, H); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,.9)'); g.fillStyle = gr; g.fillRect(0, H * .55, W, H * .45);
    grain(.09); vignette();
    if (tt < 1.4) { g.fillStyle = `rgba(0,0,0,${1 - tt / 1.4})`; g.fillRect(0, 0, W, H); }
    place = { loc: 'lobby', rain: 'glass' };
  } else if (mode !== 'play') {
    // фон выбора/концовки: последний кадр затемнён, медленно дышит
    if (mode === 'studio' || mode === 'load') { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); }   // заставка и загрузка — на чёрном
    else { shot(lastDrawn || TITLE_SHOT, { cx: .5, cy: .5, z: 1.02 + .03 * Math.sin(T * .3), t: T, T }); if (mode === 'choice') knockSync(T); g.fillStyle = `rgba(0,0,0,${mode === 'choice' ? .45 : .62})`; g.fillRect(0, 0, W, H); grain(.1); vignette(); place = placeOf(lastDrawn); }
  }
  setCamHud(cam && mode === 'play');
  if (T - flashT < .2 && !calm()) { g.fillStyle = `rgba(255,255,255,${(1 - (T - flashT) / .2) * .7})`; g.fillRect(0, 0, W, H); }
  if (T - blackT < .09) { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); }   // склейка/скример: пара чёрных кадров, без белой вспышки
  // звук места: фон, далёкие звуки, лампа
  if (AC && bed) {
    const lv = mode === 'play' || mode === 'cams' || mode === 'mg' || mode === 'choice' ? (voiceNow ? .75 : 1) : mode === 'menu' ? (gateDone ? .9 : 0) : mode === 'end' ? .5 : 0;
    bedSet(place && place.loc, place && place.rain, lv);
    if (lv > 0) { ambTick(T, mode === 'menu' ? 'title' : bedWant.loc, quiet || voiceNow || mode === 'mg'); tubeTick(T); }
  }
  requestAnimationFrame(frame);
}
function trigTick(t) { for (const e of trig) if (!e.done && t >= e.at) { e.done = true; try { e.fn(); } catch (er) { console.error(er); } } }

/* ---------- старт ---------- */
$('bMute').onclick = () => setMute(!LS.get('mute', false));
$('bMute').classList.toggle('muted', LS.get('mute', false));
$('bMenu').onclick = () => menu();
$('bRules').onclick = e => { e.stopPropagation(); showRules(); };
$('bAuto').onclick = e => { e.stopPropagation(); auto = !auto; LS.set('auto', auto); $('bAuto').classList.toggle('on', auto); };
$('bAuto').classList.toggle('on', auto);
$('stage').addEventListener('pointerdown', e => { if (e.target.closest('button, #choice, #mg, #ui, #hud, #note')) return; tap(); });
document.addEventListener('keydown', e => {
  if (mode === 'menu' && !gateDone && (e.key === ' ' || e.key === 'Enter')) return openGate();
  if (e.key === ' ' || e.key === 'Enter') tap(); if (mode === 'choice' && /^[1-4]$/.test(e.key)) { const b = choiceEl.querySelector(`button[data-i="${+e.key - 1}"]`); if (b) b.click(); } if (e.key === 'Escape') menu(); });
requestAnimationFrame(frame);
window.addEventListener('error', e => { if (mode === 'menu' && !uiEl.innerHTML) uiEl.innerHTML = `<div class="screen"><p>Ошибка: ${String(e.message || e).slice(0, 120)}</p></div>`; });
// холодный старт: заставка студии 1.4 с (тап — пропустить) идёт, пока грузится мир; потом — карточка «наушники» с полосой загрузки, если ещё грузится
let loaded = false, splashDone = false, cardT = 0;
function studio() {
  mode = 'studio';
  uiEl.innerHTML = `<div class="studio"><div class="stw"><div class="logo"><i></i><b>YOMPIX</b><span>GAMES</span></div></div></div>`;
  const go = () => { if (splashDone) return; splashDone = true; uiEl.onclick = null; afterSplash(); };
  uiEl.onclick = go; setTimeout(go, 1400);
}
function afterSplash() {
  if (loaded) return menu();
  mode = 'load'; cardT = now();
  uiEl.innerHTML = `<div class="screen boot"><div class="card">${ICON.phones}<span>18+ · жестокость и кровь · лучше в наушниках · в игре есть вспышки света и резкие звуки</span></div><div id="loadbar"><i style="width:${loadP * 100}%"></i></div></div>`;
}
studio();
load(p => { loadP = p; const b = document.querySelector('#loadbar i'); if (b) b.style.width = p * 100 + '%'; }).catch(() => {}).then(() => {
  initAudio(); loaded = true;
  if (splashDone) setTimeout(menu, Math.max(0, 1200 - (now() - cardT) * 1000));   // карточку не мелькаем — хотя бы секунда
});
window.__ns = { back: () => menu(), startScene, get mode() { return mode; }, get scene() { return sceneId; }, skipBeat, showSms, get dbg() { return [clockT.toFixed(2), bT0.toFixed(2), bDur, bi, holding, waiting, paused, pre].join(' '); } };
})();
