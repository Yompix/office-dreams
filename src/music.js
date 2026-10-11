/* ============================================================
   OFFICE DREAMS — background music: a tiny chiptune sequencer.
   Fourteen original looping tracks, synthesized with WebAudio
   (no audio files, nothing to download). Starts after the first
   user gesture (browser autoplay rules).
   ============================================================ */
const NOTE = (m) => 440 * Math.pow(2, (m - 69) / 12);
// chord = [root midi, 'm' | 'M' | 'm7' | 'M7']
const CHORD_TONES = { m: [0, 3, 7, 12], M: [0, 4, 7, 12], m7: [0, 3, 7, 10], M7: [0, 4, 7, 11] };
// lead/arp patterns: 16 steps per bar; numbers index the chord tones (+4 = next octave), null = rest
const TRACKS = [
  {
    id: 'office', bpm: 96, swing: 0.08,
    chords: [[57, 'm7'], [53, 'M7'], [48, 'M'], [55, 'M']], // Am7 Fmaj7 C G
    arp: [0, 2, 1, 3, 0, 2, 1, 3, 0, 2, 1, 3, 0, 2, 1, 3],
    lead: [[4, null, null, 6, null, 5, null, null, 4, null, 2, null, null, null, null, null], [null, null, 5, null, 6, null, 7, null, 6, null, 5, null, 4, null, null, null]],
    bass: [0, null, null, 0, null, null, 0, null, 0, null, null, 0, null, 2, null, null],
    kick: [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1],
    arpVol: 0.5, leadVol: 0.7,
  },
  {
    id: 'night', bpm: 82, swing: 0.12,
    chords: [[50, 'm7'], [46, 'M7'], [53, 'M'], [48, 'M']], // Dm7 Bbmaj7 F C
    arp: [0, null, 2, null, 1, null, 3, null, 2, null, 0, null, 3, null, 1, null],
    lead: [[null, null, null, null, 4, null, null, null, 5, null, 4, null, 2, null, null, null], [null, null, null, null, 6, null, 5, null, 4, null, null, null, null, null, null, null]],
    bass: [0, null, null, null, null, null, null, null, 0, null, null, null, 2, null, null, null],
    kick: [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], hat: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0],
    arpVol: 0.45, leadVol: 0.55,
  },
  {
    id: 'release', bpm: 120, swing: 0,
    chords: [[48, 'M'], [55, 'M'], [57, 'm'], [53, 'M']], // C G Am F
    arp: [0, 1, 2, 3, 2, 1, 0, 1, 2, 3, 2, 1, 0, 1, 2, 3],
    lead: [[4, null, 5, null, 6, null, null, 5, 6, null, 7, null, 6, null, 5, null], [6, null, null, 5, 4, null, 5, null, 6, null, null, null, 4, null, null, null]],
    bass: [0, null, 0, null, 2, null, 0, null, 0, null, 0, null, 2, null, 3, null],
    kick: [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0],
    arpVol: 0.45, leadVol: 0.75,
  },
  {
    id: 'rush', bpm: 128, swing: 0,
    chords: [[52, 'm'], [48, 'M'], [55, 'M'], [50, 'M']], // Em C G D
    arp: [0, 2, 3, 2, 0, 2, 3, 2, 0, 2, 3, 2, 0, 2, 3, 2],
    lead: [[4, 4, null, 6, null, 4, null, 7, null, 6, null, 4, null, 5, null, null], [6, null, 6, null, 7, null, 6, null, 5, null, 4, null, 5, null, 6, null]],
    bass: [0, null, 0, 0, null, 0, null, 0, 0, null, 0, 0, null, 2, null, 3],
    kick: [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1], hat: [1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1],
    arpVol: 0.4, leadVol: 0.7,
  },
  {
    id: 'lofi', bpm: 78, swing: 0.16,
    chords: [[53, 'M7'], [52, 'm7'], [50, 'm7'], [48, 'M7']], // Fmaj7 Em7 Dm7 Cmaj7
    arp: [0, null, null, 3, null, null, 2, null, 1, null, null, 3, null, 2, null, null],
    lead: [[null, null, 6, null, null, 5, null, null, 7, null, null, null, 6, null, null, null], [5, null, null, null, 4, null, 3, null, null, null, 4, null, null, null, null, null]],
    bass: [0, null, null, null, null, null, 2, null, 0, null, null, null, null, null, null, 1],
    kick: [1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1],
    arpVol: 0.4, leadVol: 0.5,
  },
  {
    id: 'synth', bpm: 108, swing: 0,
    chords: [[50, 'm'], [46, 'M'], [48, 'M'], [45, 'm']], // Dm Bb C Am
    arp: [0, 0, 4, 0, 2, 0, 4, 2, 0, 0, 4, 0, 2, 0, 5, 2],
    lead: [[6, null, null, null, 5, null, null, null, 4, null, 5, null, 6, null, null, null], [7, null, null, 6, null, null, 5, null, 6, null, null, null, null, null, 4, null]],
    bass: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2],
    kick: [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0],
    arpVol: 0.5, leadVol: 0.65,
  },
  {
    id: 'arcade', bpm: 140, swing: 0,
    chords: [[48, 'M'], [45, 'm'], [53, 'M'], [55, 'M']], // C Am F G
    arp: [0, 1, 2, 4, 2, 1, 0, 1, 2, 4, 5, 4, 2, 1, 0, 1],
    lead: [[4, null, 4, 5, 6, null, 4, null, 7, null, 6, null, 5, null, null, null], [6, 5, 4, null, 5, null, 6, null, 4, null, 2, null, 4, null, null, null]],
    bass: [0, null, 2, null, 0, null, 2, null, 0, null, 2, null, 0, 2, 3, 2],
    kick: [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0], snare: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0], hat: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
    arpVol: 0.42, leadVol: 0.7,
  },
  {
    id: 'rain', bpm: 72, swing: 0.1,
    chords: [[48, 'm'], [44, 'M'], [51, 'M'], [46, 'M']], // Cm Ab Eb Bb
    arp: [4, null, 2, null, 1, null, 0, null, 4, null, 2, null, 1, null, 2, null],
    lead: [[null, null, null, null, null, null, 6, null, null, null, 5, null, null, null, null, null], [null, null, 4, null, null, null, null, null, 5, null, null, null, 2, null, null, null]],
    bass: [0, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null],
    kick: [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0],
    arpVol: 0.5, leadVol: 0.5,
  },
  {
    id: 'boss', bpm: 132, swing: 0,
    chords: [[52, 'm'], [52, 'm'], [50, 'M'], [48, 'M']], // Em Em D C
    arp: [0, 0, 1, 0, 0, 2, 0, 1, 0, 0, 1, 0, 3, 2, 1, 0],
    lead: [[4, null, null, 4, null, null, 5, null, 6, null, 5, null, 4, null, 3, null], [7, null, 6, null, 5, null, 4, null, 5, 5, null, 6, null, null, null, null]],
    bass: [0, 0, null, 0, 0, null, 0, 0, 0, 0, null, 0, 2, null, 1, null],
    kick: [1, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1], hat: [1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1],
    arpVol: 0.4, leadVol: 0.75,
  },
  {
    id: 'sunrise', bpm: 100, swing: 0.04,
    chords: [[55, 'M'], [50, 'M'], [52, 'm'], [48, 'M']], // G D Em C
    arp: [0, 2, 4, 2, 1, 2, 4, 2, 0, 2, 4, 2, 1, 2, 5, 2],
    lead: [[null, null, 4, null, 5, null, 6, null, null, null, 6, null, 5, null, 4, null], [5, null, null, null, 6, null, null, 7, null, null, 6, null, null, null, null, null]],
    bass: [0, null, null, 2, null, null, 0, null, 1, null, null, 2, null, null, 0, null],
    kick: [1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0],
    arpVol: 0.45, leadVol: 0.6,
  },
  {
    id: 'coffee', bpm: 90, swing: 0.22,
    chords: [[50, 'm7'], [55, 'M'], [48, 'M7'], [57, 'm7']], // Dm7 G Cmaj7 Am7
    arp: [0, null, 1, 2, null, 3, null, 2, 0, null, 1, 2, null, 3, 2, null],
    lead: [[4, null, 5, null, null, 6, null, 7, null, 6, null, null, 5, null, null, null], [null, 7, null, 6, null, null, 4, null, 5, null, null, 3, null, null, null, null]],
    bass: [0, null, null, 1, null, null, 2, null, 3, null, null, 2, null, null, 1, null],
    kick: [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1],
    arpVol: 0.4, leadVol: 0.6,
  },
  {
    id: 'cyber', bpm: 120, swing: 0,
    chords: [[54, 'm'], [50, 'M'], [57, 'M'], [52, 'M']], // F#m D A E
    arp: [0, 4, 0, 2, 0, 4, 0, 1, 0, 4, 0, 2, 0, 4, 1, 2],
    lead: [[null, null, null, null, 6, null, 5, 4, null, null, null, null, 4, null, 5, null], [6, null, null, null, null, null, 7, null, 6, null, 5, null, 4, null, null, null]],
    bass: [0, null, 0, null, null, 0, null, 0, 0, null, 0, null, null, 0, 2, null],
    kick: [1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0],
    arpVol: 0.48, leadVol: 0.65,
  },
  {
    id: 'victory', bpm: 116, swing: 0,
    chords: [[46, 'M'], [53, 'M'], [55, 'm'], [51, 'M']], // Bb F Gm Eb
    arp: [0, 1, 2, 1, 0, 1, 2, 1, 4, 2, 1, 2, 4, 2, 1, 2],
    lead: [[4, null, null, 5, 6, null, null, null, 7, null, 6, null, 5, 6, null, null], [4, null, 5, null, 6, null, 7, null, 8, null, null, null, 7, null, null, null]],
    bass: [0, null, null, null, 2, null, null, null, 0, null, null, null, 2, null, 1, null],
    kick: [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1], snare: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0],
    arpVol: 0.45, leadVol: 0.75,
  },
  {
    id: 'dream', bpm: 88, swing: 0.06,
    chords: [[51, 'M7'], [46, 'M7'], [48, 'm7'], [53, 'M']], // Ebmaj7 Bbmaj7 Cm7 F
    arp: [0, 3, 2, 3, 1, 3, 2, 3, 0, 3, 2, 3, 1, 3, 4, 3],
    lead: [[null, null, null, null, 7, null, null, null, 6, null, null, null, 5, null, null, null], [6, null, null, null, null, null, 5, null, 4, null, null, null, null, null, null, null]],
    bass: [0, null, null, null, null, null, null, null, 2, null, null, null, null, null, null, null],
    kick: [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], snare: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], hat: [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1],
    arpVol: 0.5, leadVol: 0.5,
  },
];
const MUSIC = { on: false, ctx: null, out: null, noise: null, track: 0, step: 0, bar: 0, loops: 0, next: 0, timer: 0 };

function musicInit() {
  const a = UI.audio;
  if (!a || MUSIC.ctx) return !!MUSIC.ctx;
  MUSIC.ctx = a;
  MUSIC.out = a.createGain();
  MUSIC.out.gain.value = 0.0;
  // gentle low-pass so the square waves stay soft in the background
  const lp = a.createBiquadFilter();
  lp.type = 'lowpass';
  lp.frequency.value = 2600;
  MUSIC.out.connect(lp);
  lp.connect(a.destination);
  const len = a.sampleRate * 0.5;
  MUSIC.noise = a.createBuffer(1, len, a.sampleRate);
  const d = MUSIC.noise.getChannelData(0);
  let seed = 7;
  for (let i = 0; i < len; i++) { seed = (seed * 16807) % 2147483647; d[i] = (seed / 2147483647) * 2 - 1; }
  return true;
}
function mTone(freq, t, dur, type, vol) {
  const a = MUSIC.ctx;
  const o = a.createOscillator(), g = a.createGain();
  o.type = type;
  o.frequency.value = freq;
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(vol, t + 0.012);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g);
  g.connect(MUSIC.out);
  o.start(t);
  o.stop(t + dur + 0.03);
}
function mKick(t) {
  const a = MUSIC.ctx;
  const o = a.createOscillator(), g = a.createGain();
  o.type = 'sine';
  o.frequency.setValueAtTime(140, t);
  o.frequency.exponentialRampToValueAtTime(45, t + 0.12);
  g.gain.setValueAtTime(0.9, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + 0.18);
  o.connect(g); g.connect(MUSIC.out);
  o.start(t); o.stop(t + 0.2);
}
function mNoise(t, dur, freq, vol, type) {
  const a = MUSIC.ctx;
  const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain();
  s.buffer = MUSIC.noise;
  f.type = type || 'highpass';
  f.frequency.value = freq;
  g.gain.setValueAtTime(vol, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(f); f.connect(g); g.connect(MUSIC.out);
  s.start(t, Math.random() * 0.3); s.stop(t + dur + 0.02);
}
function scheduleStep(tr, t) {
  const st = MUSIC.step % 16;
  const ch = tr.chords[MUSIC.bar % tr.chords.length];
  const tones = CHORD_TONES[ch[1]].map((x) => ch[0] + x);
  const tone = (i) => tones[i % 4] + 12 * Math.floor(i / 4);
  const s16 = 60 / tr.bpm / 4;
  if (tr.arp[st] !== null) mTone(NOTE(tone(tr.arp[st]) + 12), t, s16 * 0.9, 'square', 0.06 * tr.arpVol);
  const lead = tr.lead[Math.floor(MUSIC.bar / tr.chords.length) % tr.lead.length];
  // the lead enters on the second pass of the progression so tracks breathe
  if (MUSIC.loops > 0 && lead[st] !== null) mTone(NOTE(tone(lead[st]) + 12), t, s16 * 1.8, 'triangle', 0.16 * tr.leadVol);
  if (tr.bass[st] !== null) mTone(NOTE(tone(tr.bass[st]) - 12), t, s16 * 1.6, 'triangle', 0.32);
  if (st === 0) { // soft pad: the chord, held for the bar
    for (let i = 0; i < 3; i++) mTone(NOTE(tones[i]), t, s16 * 15, 'sawtooth', 0.012);
  }
  if (tr.kick[st]) mKick(t);
  if (tr.snare[st]) mNoise(t, 0.14, 1800, 0.22, 'bandpass');
  if (tr.hat[st]) mNoise(t, 0.04, 7000, 0.08, 'highpass');
}
function musicTick() {
  if (!MUSIC.on || !MUSIC.ctx) return;
  const a = MUSIC.ctx;
  const tr = TRACKS[MUSIC.track];
  const s16 = 60 / tr.bpm / 4;
  if (MUSIC.next < a.currentTime) MUSIC.next = a.currentTime + 0.05;
  while (MUSIC.next < a.currentTime + 0.15) {
    const sw = MUSIC.step % 2 === 1 ? tr.swing * s16 : 0;
    scheduleStep(tr, MUSIC.next + sw);
    MUSIC.next += s16;
    MUSIC.step++;
    if (MUSIC.step % 16 === 0) {
      MUSIC.bar++;
      if (MUSIC.bar % tr.chords.length === 0) {
        MUSIC.loops++;
        if (MUSIC.loops >= 6) musicNext(true); // ~1–2 minutes per track, then the next one
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
  MUSIC.out.gain.cancelScheduledValues(MUSIC.ctx.currentTime);
  MUSIC.out.gain.setTargetAtTime(0.32, MUSIC.ctx.currentTime, 0.8); // fade in
  if (!MUSIC.timer) MUSIC.timer = setInterval(musicTick, 40);
}
function musicStop() {
  if (!MUSIC.on) return;
  MUSIC.on = false;
  if (MUSIC.out) MUSIC.out.gain.setTargetAtTime(0, MUSIC.ctx.currentTime, 0.2);
  if (MUSIC.timer) { clearInterval(MUSIC.timer); MUSIC.timer = 0; }
}
function musicNext(auto) {
  MUSIC.track = (MUSIC.track + 1) % TRACKS.length;
  MUSIC.step = 0; MUSIC.bar = 0; MUSIC.loops = 0;
  if (!auto && MUSIC.ctx) MUSIC.next = MUSIC.ctx.currentTime + 0.15;
}
// follow the player's preference; called after every user gesture and when the page is hidden/shown
function musicSync() {
  const want = !!(S && S.settings && S.settings.music !== false) && !document.hidden;
  if (want && UI.audio) musicStart();
  else if (!want) musicStop();
}
