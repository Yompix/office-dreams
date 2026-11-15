#!/usr/bin/env python3
"""Own music for the YURK ad, made entirely in code (numpy): no samples, no licences.
0–3.35 s: tense ticking + rising noise (nothing loads) → 3.35 s DROP on «ЮРКНИ» → 125 bpm electro beat
(kick, clap, hats, offbeat bass, minor chord stabs) → hits on scene changes → outro.
  python3 music.py OUT.wav [seconds]"""
import sys, wave
import numpy as np

SR = 44100
OUT = sys.argv[1]
DUR = float(sys.argv[2]) if len(sys.argv) > 2 else 22.0
N = int(DUR * SR)
mix = np.zeros((N, 2))
rng = np.random.default_rng(5)
BPM = 125.0
BEAT = 60 / BPM
DROP = 3.35

def put(sig, t, gain=1.0, pan=0.0):
    i = int(t * SR)
    if i >= N or i + len(sig) <= 0: return
    s = sig[: N - i] * gain
    mix[i:i + len(s), 0] += s * (1 - max(0, pan))
    mix[i:i + len(s), 1] += s * (1 + min(0, pan))

def env(n, a=0.002, d=0.2):
    t = np.arange(n) / SR
    return np.minimum(1, t / a) * np.exp(-t / d)

def kick():
    n = int(.45 * SR); t = np.arange(n) / SR
    f = 48 + 110 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.tanh(2.2 * np.sin(ph) * np.exp(-t * 7.5)) * .9

def clap():
    n = int(.25 * SR); x = rng.standard_normal(n)
    e = np.zeros(n)
    for o in (0, .011, .022): e += np.exp(-np.maximum(0, np.arange(n) / SR - o) * 60) * (np.arange(n) / SR >= o)
    y = x * (e * .4 + np.exp(-np.arange(n) / SR * 12) * .5)
    return np.convolve(y, [1, -0.9])[:n] * .5  # brighter

def hat(open_=False):
    n = int((.18 if open_ else .05) * SR); x = rng.standard_normal(n)
    x = np.diff(np.concatenate([[0], x]))
    return x * env(n, .0005, .06 if open_ else .012) * .25

def saw(f, n):
    t = np.arange(n) / SR
    return 2 * ((t * f) % 1) - 1

def bass(f, dur):
    n = int(dur * SR)
    y = saw(f, n) * .6 + np.sin(2 * np.pi * f * np.arange(n) / SR) * .8
    y = np.convolve(y, np.ones(18) / 18, mode='same')  # low-pass-ish
    return np.tanh(y * 1.4) * env(n, .004, dur * .7) * .55

def stab(freqs, dur):
    n = int(dur * SR); y = np.zeros(n)
    for f in freqs:
        for det in (-0.006, 0, 0.007): y += saw(f * (1 + det), n)
    y = np.convolve(y, np.ones(10) / 10, mode='same')
    return y / (3 * len(freqs)) * env(n, .003, dur * .45) * .5

def riser(dur):
    n = int(dur * SR); t = np.arange(n) / SR
    x = rng.standard_normal(n)
    # sweep: more highs over time (difference filter mixed in)
    hp = np.diff(np.concatenate([[0], x]))
    y = x * (1 - t / dur) * .3 + hp * (t / dur)
    return y * (t / dur) ** 2 * .35

def tick():
    n = int(.03 * SR); t = np.arange(n) / SR
    return np.sin(2 * np.pi * 2400 * t) * np.exp(-t * 200) * .35

def boom():
    n = int(1.6 * SR); t = np.arange(n) / SR
    f = 40 + 60 * np.exp(-t * 6)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 2.2)
    y += rng.standard_normal(n) * np.exp(-t * 9) * .4
    return np.tanh(y * 1.6) * .9

def whoosh(dur=.5):
    n = int(dur * SR); t = np.arange(n) / SR
    x = rng.standard_normal(n)
    return x * np.sin(np.pi * t / dur) ** 2 * .22

def hz(semi): return 55 * 2 ** (semi / 12)  # A1 based

# ---------- intro: clock ticks + uneasy drone + riser into the drop
for i in range(int(DROP / .5)):
    put(tick(), i * .5, 1, .3 if i % 2 else -.3)
n = int(DROP * SR); t = np.arange(n) / SR
drone = (np.sin(2 * np.pi * 55 * t) + .5 * np.sin(2 * np.pi * 58.3 * t)) * .12 * np.minimum(1, t / 1)
put(drone, 0)
put(riser(1.0), DROP - 1.0)
put(boom(), DROP, 1.0)

# ---------- beat from the drop: A minor, progression Am – F – C – G (in semitones from A1)
prog = [(0, [12, 15, 19]), (-4, [8, 12, 15]), (3, [15, 19, 22]), (-2, [10, 14, 17])]
end = DUR - 1.2
b = 0
t0 = DROP
while t0 + b * BEAT < end:
    tb = t0 + b * BEAT
    bar = (b // 4) % 4
    root, chord = prog[bar]
    put(kick(), tb, .95)
    if b % 2 == 1: put(clap(), tb, .7, .1)
    for h in range(2):
        put(hat(open_=(h == 1 and b % 4 == 3)), tb + h * BEAT / 2, .9, -.25 + .5 * h)
    put(bass(hz(root), BEAT * .45), tb + BEAT / 2, 1.0)  # offbeat bass
    if b % 4 == 0: put(stab([hz(c + 12) for c in chord], BEAT * 1.6), tb, .9)
    if b % 4 == 2: put(stab([hz(c + 12) for c in chord], BEAT * .8), tb + BEAT / 2, .6)
    b += 1

# ---------- scene hits (match scene.html)
for s in (4.8, 10.2, 14.6, 18.4): put(whoosh(), s - .25, 1.0)
for s in (10.3, 11.4, 12.5): put(stab([hz(24), hz(31)], .25), s + .2, .5)  # card swishes
put(boom(), 18.4, .6)

# ---------- outro: let the last chord ring
put(stab([hz(c + 12) for c in [12, 15, 19, 24]], 1.4), end, .9)

# ---------- master: gentle compression + fade + normalise
m = np.tanh(mix * 1.3)
fade = int(.6 * SR); m[-fade:] *= np.linspace(1, 0, fade)[:, None]
m /= np.max(np.abs(m)) + 1e-9
m *= .89
with wave.open(OUT, 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((m * 32767).astype('<i2').tobytes())
print('ok', OUT, DUR)
