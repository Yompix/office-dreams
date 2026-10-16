/* ============================================================
   OFFICE DREAMS — background music: 10 original upbeat tracks.
   Synthesized live with WebAudio (no audio files, no licences): bright
   house grooves and driving "boss level" synth tracks. Each track has a form — intro → groove → lead →
   break with a filter build → drop — so it never sounds like one loop.
   Tracks follow each other in a shuffled order. Starts after the first
   user gesture (browser autoplay rules).
   ============================================================ */
const NOTE = (m) => 440 * Math.pow(2, (m - 69) / 12);
const CHORD_TONES = { min: [0, 3, 7], maj: [0, 4, 7], min7: [0, 3, 7, 10], maj7: [0, 4, 7, 11], sus2: [0, 2, 7] };
const SCALES = { minor: [0, 2, 3, 5, 7, 8, 10], major: [0, 2, 4, 5, 7, 9, 11], harm: [0, 2, 3, 5, 7, 8, 11] };
// patterns are 16-step strings: a digit/letter = note (base 36), '-' = hold, '.' = rest; drums: x = hit, X = accent
function mPat(s) {
  s = s.replace(/\s/g, '');
  const out = [];
  for (let i = 0; i < 16; i++) {
    const c = s[i];
    if (!c || c === '.' || c === '-') { out.push(null); continue; }
    let len = 1;
    while (s[i + len] === '-') len++;
    out.push({ v: c === 'o' ? 'o' : parseInt(c, 36), len });
  }
  return out;
}
const mDrum = (s) => s.replace(/\s/g, '').split('').map((c) => (c === 'X' ? 1 : c === 'x' ? 0.75 : 0));
function mTrack(t) {
  t.lead = { A: t.leadA.map(mPat), B: t.leadB.map(mPat) };
  t.bassP = mPat(t.bass); t.arpP = t.arp ? mPat(t.arp) : null; t.compP = t.comp ? mDrum(t.comp) : null;
  t.dr = { k: mDrum(t.kick), s: mDrum(t.snare), h: mDrum(t.hat), o: mDrum(t.ohat || '................') };
  return t;
}
const TRACKS = [
  mTrack({ id: 'startup_sunrise', bpm: 122, key: 65, scale: 'major', chords: [[38, 'min'], [46, 'maj'], [41, 'maj'], [48, 'maj']], // Dm Bb F C — house
    leadI: 'bell', bassI: 'sub', arpI: 'pluck', pad: true, comp: '..x...x...x...x.',
    leadA: ['5---4---2---0---', '3-2-3-4-5-------', '4---2---0-2-4---', '4-5-6-4-1-------'],
    leadB: ['9-9-7-5-7---5---', 'a-9-7-5-3-------', '7-9-b-9-7---4---', '8-6-4-6-8-------'],
    bass: '..0...0...0...0.', arp: '0.1.2.1.0.1.2.4.',
    kick: 'x...x...x...x...', snare: '....x.......x...', hat: '..x...x...x...x.', ohat: '..x...x...x...x.' }),
  mTrack({ id: 'boss_level', bpm: 132, key: 64, scale: 'harm', chords: [[40, 'min'], [48, 'maj'], [50, 'maj'], [47, 'maj']], // Em C D B — boss fight
    leadI: 'saw2', bassI: 'saw', arpI: 'pluck', pad: true,
    leadA: ['0-2-4-7-4-2-4---', '5-4-2-4-5---7---', '8-7-3-8-7-------', '6-4-6-8-6-------'],
    leadB: ['7---7-8-9---b---', '9---7---5-7-9---', 'a---8---a-b-a---', '8---6---4---6-8-'],
    bass: '0000o0000000o000', arp: '0120012001200120',
    kick: 'x...x...x...x.x.', snare: '....X.......X...', hat: 'x.xxx.xxx.xxx.xx' }),
  mTrack({ id: 'coffee_rush', bpm: 124, key: 67, scale: 'major', chords: [[40, 'min'], [48, 'maj'], [43, 'maj'], [50, 'maj']], // Em C G D — house,
    leadI: 'bell', bassI: 'sub', arpI: 'pluck', pad: true, comp: '..x...x...x...x.',
    leadA: ['5---4---2---5---', '3-2-3-4-5-------', '4---2---0-2-4---', '4-5-6-4-1-------'],
    leadB: ['9-9-7-5-7---5---', 'a-9-7-5-3-------', '7-9-b-9-7---4---', '8-6-4-6-8-------'],
    bass: '..0...0...0...0.', arp: '0.2.4.2.0.2.4.2.',
    kick: 'x...x...x...x...', snare: '....x.......x...', hat: 'xxxxxxxxxxxxxxxx', ohat: '..x...x...x...x.' }),
  mTrack({ id: 'launch_day', bpm: 120, key: 57, scale: 'major', chords: [[42, 'min'], [38, 'maj'], [45, 'maj'], [40, 'maj']], // F#m D A E — piano house,
    leadI: 'bell', bassI: 'sub', arpI: 'pluck', pad: true, comp: '..x..x....x..x..',
    leadA: ['5-4-5-7-5---2---', '3---5---7---5---', '4-2-0-2-4---7---', '6-4-1-4-6-------'],
    leadB: ['c---b-9-7---9---', 'a---9-7-5---7---', '9-7-9-b-9---7---', 'b---8---6---4---'],
    bass: '..0...0...0..00.', arp: '0124210401242104',
    kick: 'x...x...x...x...', snare: '....x.......x...', hat: '..x...x...x...x.' }),
  mTrack({ id: 'team_spirit', bpm: 126, key: 58, scale: 'major', chords: [[43, 'min'], [39, 'maj'], [46, 'maj'], [41, 'maj']], // Gm Eb Bb F — funky house,
    leadI: 'pulse', bassI: 'funk', arpI: null, pad: true, comp: '..x...x...x...x.',
    leadA: ['5---7---9---7-5-', '3---5---7-5-3---', '2-4-2-0-2-------', '1---4---6---8---'],
    leadB: ['c-b-9-b-c-------', 'a---c---a-9-7---', '9-7-9-b-9---7---', '8-6-4-6-8---b---'],
    bass: '0..0..o.0..0..o.',
    kick: 'x...x...x...x...', snare: '....x.......x..x', hat: 'x.x.x.x.x.x.x.x.', ohat: '..x...x...x...x.' }),
  mTrack({ id: 'gold_hour', bpm: 118, key: 52, scale: 'major', chords: [[37, 'min'], [45, 'maj'], [40, 'maj'], [47, 'maj']], // C#m A E B — sunset house,
    leadI: 'bell', bassI: 'sub', arpI: 'pluck', pad: true, comp: '..x...x...x...x.',
    leadA: ['5-------4-2-4---', '3---5---7---5---', '4-2-0-2-4-------', '6-4-1-4-6---8---'],
    leadB: ['9---b---c---b-9-', 'a---c---a-9-7---', 'b-9-7-9-b-------', '8---6---4---6-8-'],
    bass: '..0...0...0...0.', arp: '0.2.4.2.0.2.4.2.',
    kick: 'x...x...x...x...', snare: '....x.......x...', hat: '..x...x...x...x.', ohat: '...x...x...x...x' }),
  mTrack({ id: 'final_sprint', bpm: 136, key: 62, scale: 'harm', chords: [[38, 'min'], [46, 'maj'], [48, 'maj'], [45, 'maj']], // Dm Bb C A — final boss,
    leadI: 'saw2', bassI: 'saw', arpI: 'pluck', pad: true,
    leadA: ['0-2-4-7-4-2-4---', '5-4-2-4-5---9---', '1-3-4-3-1-------', '6-4-1-4-6---7---'],
    leadB: ['7---9---b---9-7-', 'c---b---9---b---', 'a---8---a-b-a---', 'b---6---8---b---'],
    bass: '0000o0000000o000', arp: '0120012001200120',
    kick: 'x...x...x...x.x.', snare: '....X.......X...', hat: 'x.xxx.xxx.xxx.xx' }),
  mTrack({ id: 'server_storm', bpm: 140, key: 60, scale: 'harm', chords: [[48, 'min'], [44, 'maj'], [51, 'maj'], [43, 'maj']], // Cm Ab Eb G — storm,
    leadI: 'saw2', bassI: 'saw', arpI: 'pluck', pad: true,
    leadA: ['4---4-2-0---2-4-', '5---7---5-4-2---', '2---4---7---4---', '6---4---1---6---'],
    leadB: ['b-b-9-b-c---b---', 'c---a-9-7---9---', '9---b---9-8-7---', '8---6---4---6-8-'],
    bass: '00.000.000.000.o', arp: '0124012401240124',
    kick: 'x..xx...x..xx...', snare: '....X.......X...', hat: 'xxxxxxxxxxxxxxxx' }),
  mTrack({ id: 'top_floor', bpm: 130, key: 55, scale: 'harm', chords: [[43, 'min'], [39, 'maj'], [41, 'maj'], [50, 'maj']], // Gm Eb F D — the climb,
    leadI: 'saw2', bassI: 'saw', arpI: 'pluck', pad: true,
    leadA: ['7---4---2---4-7-', '5---7---9---7---', '8---a---8-4-1---', '6---8---b---8-6-'],
    leadB: ['7-9-b-9-7---4---', 'c---9---7---9---', 'a---8---a---b---', '8---6---4---6---'],
    bass: '0.0.o.0.0.0.o.0.', arp: '0210021002100210',
    kick: 'x...x...x...x...', snare: '....X.......X.x.', hat: 'x.x.x.x.x.x.x.x.', ohat: '..x...x...x...x.' }),
  mTrack({ id: 'night_shift', bpm: 128, key: 59, scale: 'harm', chords: [[47, 'min'], [43, 'maj'], [50, 'maj'], [42, 'maj']], // Bm G D F# — night shift,
    leadI: 'saw2', bassI: 'saw', arpI: 'pluck', pad: true,
    leadA: ['4-2-0-2-4---7---', '5---4---2---0---', '2-4-9-4-2-------', '6-4-1-4-6-------'],
    leadB: ['b---9---7-9-b---', 'c---b---9---7---', '9---b---9-8-7---', '8---6---4---6-8-'],
    bass: '0.00o.000.00o.0.', arp: '0124012401240124',
    kick: 'x...x...x...x...', snare: '....X.......X...', hat: 'x.xxx.xxx.xxx.xx' }),
];
// the form of every track, one entry per pass through its chords (4 bars):
// k kick · s snare · h hats · b bass · p pad/comp · a arp · l lead (A/B) · up = filter build
const FORM = [
  { k: 1, h: 1, b: 1, p: 1 },
  { k: 1, s: 1, h: 1, b: 1, p: 1, a: 1, fill: 1 },
  { k: 1, s: 1, h: 1, b: 1, p: 1, a: 1, l: 'A' },
  { k: 1, s: 1, h: 1, b: 1, p: 1, a: 1, l: 'B', fill: 1 },
  { p: 1, a: 1, l: 'A', up: 1, fill: 1 }, // break: no drums, the filter opens up
  { k: 1, s: 1, h: 1, b: 1, p: 1, a: 1, l: 'B' },
  { k: 1, s: 1, h: 1, b: 1, p: 1, a: 1, l: 'A' },
  { k: 1, s: 1, h: 1, b: 1, p: 1, l: 'B', fill: 1 },
];
const MUSIC = { on: false, ctx: null, out: null, lp: null, pad: null, lead: null, dly: null, dlyIn: null, noise: null, track: 0, step: 0, bar: 0, loops: 0, next: 0, timer: 0, order: [] };

function musicInit(a) {
  a = a || UI.audio;
  if (!a) return false;
  if (MUSIC.ctx === a) return true;
  MUSIC.ctx = a;
  MUSIC.out = a.createGain();
  MUSIC.out.gain.value = 0.0;
  MUSIC.lp = a.createBiquadFilter(); // master tone + the build-up sweep in breaks
  MUSIC.lp.type = 'lowpass'; MUSIC.lp.frequency.value = 9000; MUSIC.lp.Q.value = 0.7;
  const comp = a.createDynamicsCompressor();
  comp.threshold.value = -16; comp.ratio.value = 4; comp.attack.value = 0.005; comp.release.value = 0.15;
  MUSIC.out.connect(MUSIC.lp); MUSIC.lp.connect(comp); comp.connect(a.destination);
  // pads and stabs duck under the kick (the classic "pumping" feel)
  MUSIC.pad = a.createGain();
  const plp = a.createBiquadFilter(); plp.type = 'lowpass'; plp.frequency.value = 1600;
  MUSIC.pad.connect(plp); plp.connect(MUSIC.out);
  // leads share one filter too: far fewer nodes per note, so weak phones keep up
  MUSIC.lead = a.createBiquadFilter(); MUSIC.lead.type = 'lowpass'; MUSIC.lead.frequency.value = 3000;
  MUSIC.lead.connect(MUSIC.out);
  // a dotted-eighth echo for leads and arps
  MUSIC.dlyIn = a.createGain(); MUSIC.dlyIn.gain.value = 0.22;
  MUSIC.dly = a.createDelay(1.5);
  const fb = a.createGain(), dlp = a.createBiquadFilter();
  fb.gain.value = 0.32; dlp.type = 'lowpass'; dlp.frequency.value = 2800;
  MUSIC.dlyIn.connect(MUSIC.dly); MUSIC.dly.connect(dlp); dlp.connect(fb); fb.connect(MUSIC.dly); dlp.connect(MUSIC.out);
  const len = a.sampleRate;
  MUSIC.noise = a.createBuffer(1, len, a.sampleRate);
  const d = MUSIC.noise.getChannelData(0);
  let seed = 7;
  for (let i = 0; i < len; i++) { seed = (seed * 16807) % 2147483647; d[i] = (seed / 2147483647) * 2 - 1; }
  return true;
}
// one oscillator voice: attack → decay to sustain → release, optional filter envelope, vibrato, echo send
function mVoice(type, freq, t, dur, vol, o) {
  const a = MUSIC.ctx;
  o = o || {};
  const osc = a.createOscillator(), g = a.createGain();
  osc.type = type; osc.frequency.value = freq;
  if (o.det) osc.detune.value = o.det;
  let node = osc;
  if (o.lp) {
    const f = a.createBiquadFilter();
    f.type = 'lowpass'; f.Q.value = o.q || 1;
    if (o.env) { f.frequency.setValueAtTime(o.lp, t); f.frequency.exponentialRampToValueAtTime(o.env, t + (o.envT || 0.12)); }
    else f.frequency.value = o.lp;
    osc.connect(f); node = f;
  }
  const at = o.a || 0.006, rel = o.r || 0.06, sus = o.s == null ? 0.7 : o.s;
  g.gain.setValueAtTime(0.0001, t);
  g.gain.linearRampToValueAtTime(vol, t + at);
  g.gain.setTargetAtTime(vol * sus, t + at, o.d || 0.08);
  g.gain.setTargetAtTime(0.0001, t + Math.max(at, dur), rel);
  node.connect(g);
  g.connect(o.bus || MUSIC.out);
  if (o.send) g.connect(MUSIC.dlyIn);
  if (o.vib) {
    const l = a.createOscillator(), lg = a.createGain();
    l.frequency.value = 5.5; lg.gain.setValueAtTime(0, t); lg.gain.linearRampToValueAtTime(freq * 0.007, t + 0.25);
    l.connect(lg); lg.connect(osc.frequency);
    l.start(t); l.stop(t + dur + rel * 6);
  }
  osc.start(t);
  osc.stop(t + dur + rel * 4);
}
const LEAD = {
  saw2: (f, t, d) => { mVoice('sawtooth', f, t, d, 0.05, { det: -9, vib: d > 0.3, bus: MUSIC.lead, send: 1 }); mVoice('sawtooth', f, t, d, 0.05, { det: 9, bus: MUSIC.lead }); },
  square: (f, t, d) => mVoice('square', f, t, d, 0.05, { s: 0.6, vib: d > 0.3, bus: MUSIC.lead, send: 1 }),
  pulse: (f, t, d) => mVoice('square', f, t, d, 0.06, { s: 0.55, d: 0.15, vib: d > 0.3, bus: MUSIC.lead, send: 1 }),
  bell: (f, t, d) => { mVoice('sine', f, t, d, 0.1, { s: 0.35, d: 0.25, r: 0.25, bus: MUSIC.lead, send: 1 }); mVoice('sine', f * 2, t, d * 0.4, 0.03, { s: 0.2, d: 0.08 }); },
};
const BASS = {
  saw: (f, t, d) => mVoice('sawtooth', f, t, d, 0.11, { lp: 1500, env: 320, envT: 0.11, q: 3, s: 0.8 }),
  tri: (f, t, d) => mVoice('triangle', f, t, d, 0.2, { s: 0.9 }),
  funk: (f, t, d) => mVoice('sawtooth', f, t, d, 0.12, { lp: 2600, env: 380, envT: 0.07, q: 7, s: 0.6 }),
  sub: (f, t, d) => { mVoice('sine', f, t, d, 0.22, { s: 0.9 }); mVoice('sawtooth', f, t, d, 0.04, { lp: 700 }); },
};
const ARP = {
  pluck: (f, t, d) => mVoice('square', f, t, d, 0.032, { lp: 3200, env: 600, envT: 0.1, s: 0.3, send: 1 }),
  chip: (f, t, d) => mVoice('square', f, t, d, 0.03, { s: 0.5, r: 0.02 }),
};
function mKick(t, v) {
  const a = MUSIC.ctx;
  const o = a.createOscillator(), g = a.createGain();
  o.type = 'sine';
  o.frequency.setValueAtTime(160, t);
  o.frequency.exponentialRampToValueAtTime(44, t + 0.11);
  g.gain.setValueAtTime(0.55 * v, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + 0.32);
  o.connect(g); g.connect(MUSIC.out);
  o.start(t); o.stop(t + 0.34);
  mNoise(t, 0.012, 3000, 0.08 * v, 'highpass'); // click
  // duck the pads
  const pg = MUSIC.pad.gain;
  pg.setValueAtTime(0.25, t); pg.linearRampToValueAtTime(1, t + 0.22);
}
function mNoise(t, dur, freq, vol, type, q) {
  const a = MUSIC.ctx;
  const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain();
  s.buffer = MUSIC.noise;
  f.type = type || 'highpass'; f.frequency.value = freq;
  if (q) f.Q.value = q;
  g.gain.setValueAtTime(vol, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(f); f.connect(g); g.connect(MUSIC.out);
  s.start(t, Math.random() * 0.6); s.stop(t + dur + 0.02);
}
function mSnare(t, v) {
  mNoise(t, 0.17, 1900, 0.16 * v, 'bandpass', 0.7);
  mNoise(t + 0.008, 0.1, 5000, 0.06 * v, 'highpass');
  mVoice('triangle', 190, t, 0.03, 0.09 * v, { s: 0.2, d: 0.03, r: 0.03 });
}
function chordTone(ch, i) {
  const ts = CHORD_TONES[ch[1]];
  return ch[0] + ts[i % ts.length] + 12 * Math.floor(i / ts.length);
}
function scaleNote(tr, deg) {
  const sc = SCALES[tr.scale];
  return tr.key + sc[deg % 7] + 12 * Math.floor(deg / 7);
}
function scheduleStep(tr, t) {
  const st = MUSIC.step % 16;
  const ci = MUSIC.bar % tr.chords.length, ch = tr.chords[ci];
  const sec = FORM[MUSIC.loops % FORM.length];
  const s16 = 60 / tr.bpm / 4;
  const hum = () => 0.85 + Math.random() * 0.15;
  const lastBar = ci === tr.chords.length - 1;
  // the build-up: in a break the master filter opens over the whole pass
  if (st === 0 && ci === 0) {
    const lf = MUSIC.lp.frequency;
    if (sec.up) { lf.setValueAtTime(700, t); lf.exponentialRampToValueAtTime(9000, t + s16 * 16 * tr.chords.length); }
    else lf.setValueAtTime(9000, t);
  }
  if (sec.k && tr.dr.k[st]) mKick(t, tr.dr.k[st]);
  if (sec.s && tr.dr.s[st]) mSnare(t, tr.dr.s[st] * hum());
  if (sec.fill && lastBar && st >= 12) mSnare(t, 0.35 + (st - 12) * 0.18); // a roll into the next part
  if (sec.h) {
    if (tr.dr.h[st]) mNoise(t, 0.03, 8000, 0.05 * tr.dr.h[st] * hum(), 'highpass');
    if (tr.dr.o[st]) mNoise(t, 0.16, 7000, 0.04 * hum(), 'highpass');
  }
  if (sec.b) {
    const b = tr.bassP[st];
    if (b) BASS[tr.bassI](NOTE(b.v === 'o' ? ch[0] + 12 : chordTone(ch, b.v)), t, s16 * b.len * 0.9);
  }
  if (sec.p) {
    if (tr.pad && st === 0) chordTones(ch).forEach((m) => { // a wide pad held for the whole bar
      mVoice('sawtooth', NOTE(m + 12), t, s16 * 15.5, 0.017, { det: -11, a: 0.25, s: 0.85, r: 0.3, bus: MUSIC.pad });
      mVoice('sawtooth', NOTE(m + 12), t, s16 * 15.5, 0.017, { det: 11, a: 0.25, s: 0.85, r: 0.3, bus: MUSIC.pad });
    });
    if (tr.compP && tr.compP[st]) chordTones(ch).forEach((m) => mVoice('square', NOTE(m + 12), t, s16 * 1.2, 0.018, { s: 0.25, d: 0.06, bus: MUSIC.pad }));
  }
  if (sec.a && tr.arpP && tr.arpI) {
    const n = tr.arpP[st];
    if (n) ARP[tr.arpI](NOTE(chordTone(ch, n.v) + 24), t, s16 * 0.8);
  }
  if (sec.l) {
    const n = tr.lead[sec.l][ci][st];
    if (n) LEAD[tr.leadI](NOTE(scaleNote(tr, n.v) + (tr.key >= 70 ? 0 : 12)), t + 0.004, s16 * n.len * 0.92);
  }
}
const chordTones = (ch) => CHORD_TONES[ch[1]].map((x) => ch[0] + x);
function musicTick() {
  if (!MUSIC.on || !MUSIC.ctx) return;
  const a = MUSIC.ctx;
  const tr = TRACKS[MUSIC.track];
  const s16 = 60 / tr.bpm / 4;
  if (MUSIC.next < a.currentTime) MUSIC.next = a.currentTime + 0.05;
  while (MUSIC.next < a.currentTime + 0.2) {
    scheduleStep(tr, MUSIC.next);
    MUSIC.next += s16;
    if (musicAdvance()) break;
  }
}
// returns true when the track changed (the next one starts on a fresh beat)
function musicAdvance() {
  const tr = TRACKS[MUSIC.track];
  MUSIC.step++;
  if (MUSIC.step % 16) return false;
  MUSIC.bar++;
  if (MUSIC.bar % tr.chords.length) return false;
  MUSIC.loops++;
  if (MUSIC.loops >= FORM.length) { musicNext(true); return true; } // ~1–1.5 minutes per track, then the next one
  return false;
}
// shuffled order: every track plays once before any repeats, never the same one twice in a row
function musicPick() {
  if (!MUSIC.order.length) {
    const o = TRACKS.map((_, i) => i);
    for (let i = o.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [o[i], o[j]] = [o[j], o[i]]; }
    if (o[0] === MUSIC.track && o.length > 1) o.push(o.shift());
    MUSIC.order = o;
  }
  return MUSIC.order.shift();
}
function musicStart() {
  if (MUSIC.on) return;
  ensureAudio();
  if (!musicInit()) return;
  if (!MUSIC.started) { MUSIC.started = true; MUSIC.track = musicPick(); }
  MUSIC.on = true;
  MUSIC.next = MUSIC.ctx.currentTime + 0.1;
  MUSIC.out.gain.cancelScheduledValues(MUSIC.ctx.currentTime);
  MUSIC.out.gain.setTargetAtTime(0.8, MUSIC.ctx.currentTime, 0.8); // fade in
  if (!MUSIC.timer) MUSIC.timer = setInterval(musicTick, 40);
}
function musicStop() {
  if (!MUSIC.on) return;
  MUSIC.on = false;
  if (MUSIC.out) MUSIC.out.gain.setTargetAtTime(0, MUSIC.ctx.currentTime, 0.2);
  if (MUSIC.timer) { clearInterval(MUSIC.timer); MUSIC.timer = 0; }
}
function musicNext(auto) {
  MUSIC.started = true;
  MUSIC.track = musicPick();
  MUSIC.step = 0; MUSIC.bar = 0; MUSIC.loops = 0;
  if (MUSIC.ctx) {
    MUSIC.dly.delayTime.setValueAtTime((60 / TRACKS[MUSIC.track].bpm) * 0.75, MUSIC.ctx.currentTime);
    if (!auto) MUSIC.next = MUSIC.ctx.currentTime + 0.15;
    else MUSIC.next += 60 / TRACKS[MUSIC.track].bpm; // one beat of air between tracks
  }
}
// follow the player's preference; called after every user gesture and when the page is hidden/shown
function musicSync() {
  const want = !!(S && S.settings && S.settings.music !== false) && !document.hidden; // on by default; the player can switch it off in Settings
  if (want && UI.audio) musicStart();
  else if (!want) musicStop();
}
// offline render of one track (used to preview the music outside the game)
function musicRender(a, idx, seconds) {
  musicInit(a);
  MUSIC.track = idx; MUSIC.step = 0; MUSIC.bar = 0; MUSIC.loops = 0;
  MUSIC.out.gain.value = 0.8;
  const tr = TRACKS[idx], s16 = 60 / tr.bpm / 4;
  MUSIC.dly.delayTime.value = (60 / tr.bpm) * 0.75;
  for (let t = 0.05; t < seconds; t += s16) {
    scheduleStep(tr, t);
    MUSIC.step++;
    if (MUSIC.step % 16 === 0) { MUSIC.bar++; if (MUSIC.bar % tr.chords.length === 0) MUSIC.loops++; }
  }
}
