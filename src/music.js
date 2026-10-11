/* ============================================================
   OFFICE DREAMS — background music: 14 original lo-fi beats.
   Synthesized live with WebAudio (no audio files): soft electric-piano
   chords, a sine melody with gentle vibrato, round bass, lazy boom-bap
   drums, vinyl crackle and a little tape wobble. Starts after the first
   user gesture (browser autoplay rules).
   ============================================================ */
const NOTE = (m) => 440 * Math.pow(2, (m - 69) / 12);
// chord = [root midi, type]; voicings are lo-fi friendly (7ths / 9ths / 11ths)
const CHORD_TONES = {
  m7: [0, 3, 7, 10], M7: [0, 4, 7, 11], m9: [0, 3, 10, 14], M9: [0, 4, 11, 14],
  d7: [0, 4, 7, 10], d9: [0, 4, 10, 14], m11: [0, 3, 10, 17], sus: [0, 5, 7, 10], M6: [0, 4, 7, 9],
};
const _ = null;
// 16 steps per bar. comp: 1 = chord stab (2 = longer); mel: chord-tone index (+4 = octave up), null = rest
const TRACKS = [
  { id: 'lf_window', bpm: 74, swing: 0.18, chords: [[48, 'M9'], [45, 'm9'], [50, 'm9'], [43, 'd9']], // Cmaj9 Am9 Dm9 G9
    comp: [2, 0, 0, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 0, 0],
    mel: [[_, _, 6, _, 5, _, _, 4, _, _, _, _, 5, _, _, _], [_, _, _, _, 6, _, 7, _, 6, _, 5, _, _, _, _, _]],
    bass: [0, _, _, _, _, _, _, 0, _, _, 2, _, _, _, _, _], kick: [1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 0] },
  { id: 'lf_rain', bpm: 68, swing: 0.2, rain: true, chords: [[53, 'M7'], [52, 'm7'], [50, 'm9'], [48, 'M9']], // Fmaj7 Em7 Dm9 Cmaj9
    comp: [2, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0],
    mel: [[_, _, _, _, 7, _, _, _, 6, _, _, _, 4, _, _, _], [_, _, _, _, _, _, 5, _, _, _, 6, _, _, _, _, _]],
    bass: [0, _, _, _, _, _, _, _, 0, _, _, _, _, _, 1, _], kick: [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0] },
  { id: 'lf_coffee', bpm: 84, swing: 0.22, chords: [[50, 'm9'], [43, 'd9'], [48, 'M9'], [45, 'm7']], // Dm9 G9 Cmaj9 Am7
    comp: [1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 0],
    mel: [[4, _, 5, _, _, 6, _, _, 7, _, 6, _, 5, _, _, _], [_, 6, _, 5, _, _, 4, _, 5, _, _, _, _, _, _, _]],
    bass: [0, _, _, 0, _, _, 2, _, 0, _, _, _, 3, _, 2, _], kick: [1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1], hat: [1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0] },
  { id: 'lf_midnight', bpm: 70, swing: 0.16, chords: [[45, 'm9'], [41, 'M7'], [48, 'M9'], [52, 'm7']], // Am9 Fmaj7 Cmaj9 Em7
    comp: [2, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0],
    mel: [[_, _, _, _, _, _, 7, _, _, _, 6, _, _, _, 5, _], [4, _, _, _, _, _, _, _, 5, _, _, _, _, _, _, _]],
    bass: [0, _, _, _, _, _, _, _, _, _, 0, _, _, _, _, _], kick: [1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0] },
  { id: 'lf_sunday', bpm: 82, swing: 0.14, chords: [[55, 'M7'], [52, 'm7'], [48, 'M7'], [50, 'd7']], // Gmaj7 Em7 Cmaj7 D7
    comp: [1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0],
    mel: [[4, _, _, 5, _, _, 6, _, _, _, 5, _, 4, _, _, _], [6, _, _, _, 7, _, _, _, 6, _, 5, _, 4, _, 2, _]],
    bass: [0, _, _, _, 2, _, _, _, 0, _, _, _, 2, _, 3, _], kick: [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0] },
  { id: 'lf_tape', bpm: 76, swing: 0.2, chords: [[51, 'M9'], [46, 'M7'], [48, 'm9'], [53, 'd9']], // Ebmaj9 Bbmaj7 Cm9 F9
    comp: [2, 0, 0, 0, 0, 1, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0],
    mel: [[_, _, 5, _, 6, _, _, _, 5, _, 4, _, _, _, _, _], [_, _, _, _, _, _, 7, _, 6, _, _, 5, _, _, 4, _]],
    bass: [0, _, _, _, _, _, 0, _, _, _, 2, _, _, _, _, _], kick: [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1] },
  { id: 'lf_city', bpm: 88, swing: 0.12, chords: [[50, 'm7'], [43, 'd7'], [48, 'M7'], [45, 'm7']], // Dm7 G7 Cmaj7 Am7
    comp: [1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0],
    mel: [[_, _, 4, _, 5, _, 6, 5, _, _, 4, _, _, _, _, _], [7, _, 6, _, _, 5, _, _, 4, _, _, _, 5, _, 6, _]],
    bass: [0, _, 0, _, _, _, 2, _, 0, _, _, 0, _, _, 3, _], kick: [1, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1] },
  { id: 'lf_dream', bpm: 66, swing: 0.1, chords: [[52, 'M9'], [49, 'm9'], [45, 'M9'], [47, 'sus']], // Emaj9 C#m9 Amaj9 Bsus
    comp: [2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0],
    mel: [[_, _, _, _, 6, _, _, _, _, _, _, _, 7, _, _, _], [_, _, _, _, _, _, _, _, 5, _, _, _, 4, _, _, _]],
    bass: [0, _, _, _, _, _, _, _, _, _, _, _, 0, _, _, _], kick: [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1] },
  { id: 'lf_study', bpm: 78, swing: 0.18, chords: [[45, 'm7'], [50, 'm9'], [52, 'm7'], [45, 'm9']], // Am7 Dm9 Em7 Am9
    comp: [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0],
    mel: [[4, _, _, _, _, _, 5, _, 6, _, _, _, 5, _, 4, _], [_, _, 2, _, 4, _, _, _, 5, _, 4, _, 2, _, _, _]],
    bass: [0, _, _, _, _, _, _, 0, _, _, 0, _, _, _, _, _], kick: [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0] },
  { id: 'lf_neon', bpm: 90, swing: 0.08, chords: [[49, 'm9'], [45, 'M7'], [47, 'd9'], [52, 'M7']], // C#m9 Amaj7 B9 Emaj7
    comp: [1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0],
    mel: [[6, _, 5, _, 4, _, _, 5, _, _, 6, _, _, _, 7, _], [6, _, _, _, 5, _, 4, _, 2, _, _, _, 4, _, _, _]],
    bass: [0, _, _, 0, _, _, 0, _, _, 0, _, _, 2, _, _, _], kick: [1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0] },
  { id: 'lf_snow', bpm: 64, swing: 0.15, chords: [[47, 'm11'], [43, 'M9'], [48, 'M9'], [42, 'm7']], // Bm11 Gmaj9 Cmaj9 F#m7
    comp: [2, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0],
    mel: [[_, _, _, _, _, _, _, _, 7, _, _, _, 6, _, _, _], [5, _, _, _, _, _, 4, _, _, _, _, _, _, _, _, _]],
    bass: [0, _, _, _, _, _, _, _, 0, _, _, _, _, _, _, _], kick: [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0] },
  { id: 'lf_bus', bpm: 80, swing: 0.2, chords: [[53, 'M6'], [52, 'm7'], [50, 'm7'], [55, 'd9']], // F6 Em7 Dm7 G9
    comp: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0],
    mel: [[4, _, 5, _, 6, _, _, _, 5, _, _, _, _, _, _, _], [_, _, _, _, 4, _, 5, _, 6, _, 7, _, 6, _, _, _]],
    bass: [0, _, _, _, _, _, _, 2, 0, _, _, _, _, _, 1, _], kick: [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1] },
  { id: 'lf_rooftop', bpm: 86, swing: 0.16, chords: [[46, 'M9'], [45, 'm7'], [43, 'm9'], [48, 'd9']], // Bbmaj9 Am7 Gm9 C9
    comp: [1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0],
    mel: [[_, _, 6, _, 7, _, 6, _, 4, _, _, _, 5, _, _, _], [6, _, _, _, _, _, 5, _, _, _, 4, _, 5, _, 6, _]],
    bass: [0, _, _, _, _, _, 0, _, _, 2, _, _, _, _, 3, _], kick: [1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0], hat: [1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0] },
  { id: 'lf_home', bpm: 72, swing: 0.18, chords: [[48, 'M7'], [52, 'm7'], [45, 'm9'], [41, 'M9']], // Cmaj7 Em7 Am9 Fmaj9
    comp: [2, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0],
    mel: [[_, _, 4, _, _, _, 5, _, _, _, 6, _, _, _, 7, _], [6, _, _, _, 5, _, _, _, 4, _, _, _, _, _, _, _]],
    bass: [0, _, _, _, _, _, _, _, 0, _, 2, _, _, _, _, _], kick: [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0] },
];
const MUSIC = { on: false, ctx: null, out: null, noise: null, vinylBuf: null, vinyl: null, rain: null, track: 0, step: 0, bar: 0, loops: 0, next: 0, timer: 0 };

function musicInit() {
  const a = UI.audio;
  if (!a || MUSIC.ctx) return !!MUSIC.ctx;
  MUSIC.ctx = a;
  MUSIC.out = a.createGain();
  MUSIC.out.gain.value = 0.0;
  // warm, dusty top end: lo-fi lives under ~2 kHz
  const lp = a.createBiquadFilter();
  lp.type = 'lowpass';
  lp.frequency.value = 1900;
  lp.Q.value = 0.4;
  MUSIC.out.connect(lp);
  lp.connect(a.destination);
  const len = a.sampleRate * 1;
  MUSIC.noise = a.createBuffer(1, len, a.sampleRate);
  const d = MUSIC.noise.getChannelData(0);
  let seed = 7;
  const rnd = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
  for (let i = 0; i < len; i++) d[i] = rnd() * 2 - 1;
  // vinyl: soft hiss + sparse crackles, looped under everything
  const vl = a.sampleRate * 3;
  const vb = a.createBuffer(1, vl, a.sampleRate);
  const v = vb.getChannelData(0);
  for (let i = 0; i < vl; i++) {
    let x = (rnd() * 2 - 1) * 0.05;
    if (rnd() < 0.0006) x += (rnd() < 0.5 ? -1 : 1) * (0.4 + rnd() * 0.6);
    v[i] = x;
  }
  MUSIC.vinylBuf = vb;
  return true;
}
function startBed(tr) {
  const a = MUSIC.ctx;
  try { if (MUSIC.vinyl) MUSIC.vinyl.stop(); } catch (e) {}
  try { if (MUSIC.rain) MUSIC.rain.stop(); } catch (e) {}
  MUSIC.vinyl = MUSIC.rain = null;
  const s = a.createBufferSource(), hp = a.createBiquadFilter(), g = a.createGain();
  s.buffer = MUSIC.vinylBuf; s.loop = true;
  hp.type = 'highpass'; hp.frequency.value = 900;
  g.gain.value = 0.09;
  s.connect(hp); hp.connect(g); g.connect(MUSIC.out);
  s.start();
  MUSIC.vinyl = s;
  if (tr.rain) { // soft rain against the window
    const r = a.createBufferSource(), bp = a.createBiquadFilter(), rg = a.createGain();
    r.buffer = MUSIC.noise; r.loop = true;
    bp.type = 'bandpass'; bp.frequency.value = 2400; bp.Q.value = 0.3;
    rg.gain.value = 0.05;
    r.connect(bp); bp.connect(rg); rg.connect(MUSIC.out);
    r.start();
    MUSIC.rain = r;
  }
}
// tape wobble: every note is a few cents off, drifting slowly
const wobble = (t) => Math.sin(t * 0.7) * 7 + Math.sin(t * 2.3) * 3;
function mNote(freq, t, dur, type, vol, opt) {
  const a = MUSIC.ctx;
  opt = opt || {};
  const o = a.createOscillator(), g = a.createGain();
  o.type = type;
  o.frequency.value = freq;
  o.detune.value = wobble(t) + (opt.detune || 0);
  const att = opt.att || 0.012;
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(vol, t + att);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g);
  g.connect(MUSIC.out);
  if (opt.vib) { // gentle vibrato for the melody
    const l = a.createOscillator(), lg = a.createGain();
    l.frequency.value = 5.2; lg.gain.value = freq * 0.006;
    l.connect(lg); lg.connect(o.frequency);
    l.start(t); l.stop(t + dur + 0.05);
  }
  o.start(t);
  o.stop(t + dur + 0.05);
}
// electric-piano-ish: sine body + quiet octave bell, slow decay
function mKey(m, t, dur, vol) {
  mNote(NOTE(m), t, dur, 'sine', vol, { att: 0.008 });
  mNote(NOTE(m + 12), t, dur * 0.45, 'triangle', vol * 0.18, { att: 0.004, detune: 4 });
}
function mKick(t, v) {
  const a = MUSIC.ctx;
  const o = a.createOscillator(), g = a.createGain();
  o.type = 'sine';
  o.frequency.setValueAtTime(110, t);
  o.frequency.exponentialRampToValueAtTime(42, t + 0.14);
  g.gain.setValueAtTime(0.75 * v, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + 0.28);
  o.connect(g); g.connect(MUSIC.out);
  o.start(t); o.stop(t + 0.3);
}
function mNoise(t, dur, freq, vol, type, q) {
  const a = MUSIC.ctx;
  const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain();
  s.buffer = MUSIC.noise;
  f.type = type || 'highpass';
  f.frequency.value = freq;
  if (q) f.Q.value = q;
  g.gain.setValueAtTime(vol, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(f); f.connect(g); g.connect(MUSIC.out);
  s.start(t, Math.random() * 0.6); s.stop(t + dur + 0.02);
}
function scheduleStep(tr, t) {
  const st = MUSIC.step % 16;
  const ch = tr.chords[MUSIC.bar % tr.chords.length];
  const tones = CHORD_TONES[ch[1]].map((x) => ch[0] + x);
  const tone = (i) => tones[i % tones.length] + 12 * Math.floor(i / tones.length);
  const s16 = 60 / tr.bpm / 4;
  const hum = () => 0.82 + Math.random() * 0.18; // human velocity
  // chords: lazy, slightly strummed stabs
  if (tr.comp[st]) {
    const dur = s16 * (tr.comp[st] === 2 ? 14 : 5);
    tones.forEach((m, i) => mKey(m + 12, t + i * 0.012, dur, 0.05 * hum()));
  }
  // melody arrives on the second pass so every track breathes in first
  const mel = tr.mel[Math.floor(MUSIC.bar / tr.chords.length) % tr.mel.length];
  if (MUSIC.loops > 0 && mel[st] !== null) mNote(NOTE(tone(mel[st]) + 24), t + 0.01, s16 * 3.2, 'sine', 0.085 * hum(), { vib: true, att: 0.02 });
  if (tr.bass[st] !== null) mNote(NOTE(tone(tr.bass[st]) - 12), t, s16 * 3, 'sine', 0.36, { att: 0.01 });
  if (tr.kick[st]) mKick(t, hum());
  if (tr.snare[st]) { mNoise(t + 0.015, 0.18, 1500, 0.16 * hum(), 'bandpass', 0.8); mNoise(t + 0.015, 0.08, 400, 0.05, 'lowpass'); }
  if (tr.hat[st]) mNoise(t, 0.035, 6500, (st % 4 === 2 ? 0.045 : 0.03) * hum(), 'highpass');
}
function musicTick() {
  if (!MUSIC.on || !MUSIC.ctx) return;
  const a = MUSIC.ctx;
  const tr = TRACKS[MUSIC.track];
  const s16 = 60 / tr.bpm / 4;
  if (MUSIC.next < a.currentTime) MUSIC.next = a.currentTime + 0.05;
  while (MUSIC.next < a.currentTime + 0.2) {
    const sw = MUSIC.step % 2 === 1 ? tr.swing * s16 : 0;
    scheduleStep(tr, MUSIC.next + sw);
    MUSIC.next += s16;
    MUSIC.step++;
    if (MUSIC.step % 16 === 0) {
      MUSIC.bar++;
      if (MUSIC.bar % tr.chords.length === 0) {
        MUSIC.loops++;
        if (MUSIC.loops >= 6) musicNext(true); // ~2–3 minutes per track, then the next one
      }
    }
  }
}
function musicStart() {
  if (MUSIC.on) return;
  ensureAudio();
  if (!musicInit()) return;
  MUSIC.on = true;
  MUSIC.next = MUSIC.ctx.currentTime + 0.1;
  startBed(TRACKS[MUSIC.track]);
  MUSIC.out.gain.cancelScheduledValues(MUSIC.ctx.currentTime);
  MUSIC.out.gain.setTargetAtTime(0.42, MUSIC.ctx.currentTime, 1.2); // slow fade in
  if (!MUSIC.timer) MUSIC.timer = setInterval(musicTick, 40);
}
function musicStop() {
  if (!MUSIC.on) return;
  MUSIC.on = false;
  if (MUSIC.out) MUSIC.out.gain.setTargetAtTime(0, MUSIC.ctx.currentTime, 0.2);
  if (MUSIC.timer) { clearInterval(MUSIC.timer); MUSIC.timer = 0; }
  setTimeout(() => { if (!MUSIC.on) { try { if (MUSIC.vinyl) MUSIC.vinyl.stop(); if (MUSIC.rain) MUSIC.rain.stop(); } catch (e) {} MUSIC.vinyl = MUSIC.rain = null; } }, 800);
}
function musicNext(auto) {
  MUSIC.track = (MUSIC.track + 1) % TRACKS.length;
  MUSIC.step = 0; MUSIC.bar = 0; MUSIC.loops = 0;
  if (MUSIC.on && MUSIC.ctx) startBed(TRACKS[MUSIC.track]);
  if (!auto && MUSIC.ctx) MUSIC.next = MUSIC.ctx.currentTime + 0.15;
}
// follow the player's preference; called after every user gesture and when the page is hidden/shown
function musicSync() {
  const want = !!(S && S.settings && S.settings.music !== false) && !document.hidden;
  if (want && UI.audio) musicStart();
  else if (!want) musicStop();
}
