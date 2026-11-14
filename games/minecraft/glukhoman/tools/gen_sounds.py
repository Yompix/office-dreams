#!/usr/bin/env python3
"""Все звуки «Глухомани» синтезируются кодом (без чужих сэмплов): фоны (лес, посёлок, шахта, станция, бункер, страх,
облегчение, погоня), сердцебиение, дыхание, стук, шаги, треск, рация, скримеры, голоса существ (формантный синтез Хора).
Запуск: /tmp/claude-0/ttsenv/bin/python tools/gen_sounds.py   (нужны numpy, scipy, ffmpeg)
"""
import os, subprocess, tempfile
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

SR = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'glukhoman', 'sounds')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1996)


def t_(s):
    return np.arange(int(s * SR)) / SR


def noise(s):
    return rng.standard_normal(int(s * SR))


def lp(x, f, o=2):
    return sosfilt(butter(o, f, 'low', fs=SR, output='sos'), x)


def hp(x, f, o=2):
    return sosfilt(butter(o, f, 'high', fs=SR, output='sos'), x)


def bp(x, lo, hi, o=2):
    return sosfilt(butter(o, [lo, hi], 'band', fs=SR, output='sos'), x)


def env(n, a, r, shape=1.0):
    n = int(n)
    e = np.ones(n)
    ai, ri = max(1, int(a * SR)), max(1, int(r * SR))
    e[:ai] = np.linspace(0, 1, ai) ** shape
    e[-ri:] *= np.linspace(1, 0, ri) ** shape
    return e


def decay(s, k):
    return np.exp(-t_(s) * k)


def reverb(x, size=1.8, mix=0.35):
    ir = noise(size) * np.exp(-t_(size) * 4.5 / size)
    ir = lp(ir, 5000)
    y = fftconvolve(x, ir)[:len(x) + int(size * SR)]
    y = y / (np.abs(y).max() + 1e-9) * np.abs(x).max()
    out = np.zeros(len(y))
    out[:len(x)] += x * (1 - mix)
    return out + y * mix


def place(buf, snd, at, g=1.0, wrap=True):
    i = int(at * SR) % len(buf)
    n = min(len(snd), len(buf) - i)
    buf[i:i + n] += snd[:n] * g
    if wrap and n < len(snd):
        buf[:len(snd) - n] += snd[n:] * g


def loopify(x, xf=1.0):
    k = int(xf * SR)
    head, tail = x[:k].copy(), x[-k:].copy()
    w = np.linspace(0, 1, k)
    x = x[:-k].copy()
    x[:k] = head * w + tail * (1 - w)
    return x


def norm(x, peak=0.9):
    return x / (np.abs(x).max() + 1e-9) * peak


def save(name, x, peak=0.9):
    x = norm(np.asarray(x, dtype=np.float64), peak)
    path = os.path.join(OUT, name.replace('.', '/') + '.ogg') if name.startswith('amb.') else os.path.join(OUT, name + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.raw') as f:
        f.write((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
        f.flush()
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', f.name, '-c:a', 'libvorbis', '-q:a', '3', path], check=True)
    print('ok', name, round(len(x) / SR, 1), 's')


# ---------------- кирпичики ----------------
def wind(s, level=1.0, gust=0.3):
    n = noise(s)
    x = bp(n, 150, 900) * 0.6 + lp(n, 300) * 0.8
    mod = lp(rng.standard_normal(len(x)), 0.4, 1)
    mod = 0.55 + gust * mod / (np.abs(mod).max() + 1e-9)
    return x * mod * level


def creak(dur=1.2, f0=None):
    f0 = f0 or rng.uniform(90, 180)
    t = t_(dur)
    f = f0 * (1 + 0.25 * np.sin(np.pi * t / dur)) + rng.uniform(-8, 8) * np.sin(2 * np.pi * 7 * t)
    ph = 2 * np.pi * np.cumsum(f) / SR
    pulses = (np.sin(ph) > 0.95).astype(float)
    x = bp(pulses * 1.0 + np.sin(ph) * 0.1, 120, 2500)
    return x * env(len(t), 0.1, 0.3)


def thump(f=55, dur=0.35, k=14):
    t = t_(dur)
    return np.sin(2 * np.pi * f * t * (1 - 0.3 * t / dur)) * np.exp(-t * k)


def click(dur=0.012):
    return hp(noise(dur), 1500) * decay(dur, 300)


def knock1():
    t = t_(0.18)
    body = np.sin(2 * np.pi * 170 * t) * 0.7 + np.sin(2 * np.pi * 410 * t) * 0.35
    return (body + bp(noise(0.18), 300, 2000) * 0.5) * np.exp(-t * 35)


def twig():
    n = noise(0.08)
    x = bp(n, 1200, 6000) * decay(0.08, 70)
    y = np.zeros(int(0.25 * SR))
    for _ in range(rng.integers(2, 5)):
        place(y, x * rng.uniform(0.3, 1), rng.uniform(0, 0.15), wrap=False)
    return y


def step_forest():
    n = noise(0.22)
    return (lp(n, 900) * 0.8 + bp(n, 1500, 5000) * 0.3) * env(len(n), 0.01, 0.18, 2) + thump(70, 0.22, 25) * 0.5


def drip():
    t = t_(0.25)
    f = rng.uniform(900, 1700)
    return np.sin(2 * np.pi * f * t * (1 + 0.6 * np.exp(-t * 30))) * np.exp(-t * 22)


def formant_voice(dur, f0, vowel='a', vib=5.0, jitter=0.02):
    F = {'a': (800, 1150, 2900), 'o': (450, 800, 2830), 'u': (325, 700, 2530), 'e': (400, 1700, 2600)}[vowel]
    t = t_(dur)
    f = f0 * (1 + 0.012 * np.sin(2 * np.pi * vib * t) + jitter * lp(rng.standard_normal(len(t)), 3, 1) * 8)
    ph = np.cumsum(f) / SR
    saw = 2 * (ph % 1) - 1
    x = sum(bp(saw, fo * 0.85, fo * 1.15) * g for fo, g in zip(F, (1, 0.6, 0.25)))
    return x


def choir(dur, f0s, vowel='o'):
    x = np.zeros(int(dur * SR))
    for f0 in f0s:
        v = formant_voice(dur, f0 * rng.uniform(0.98, 1.02), vowel, vib=rng.uniform(4, 6.5))
        x += v[:len(x)]
    return x


# ---------------- фоны ----------------
def amb_forest():
    L = 32
    x = wind(L + 1, 1.0, 0.5)
    x += np.sin(2 * np.pi * 43 * t_(L + 1)) * 0.06
    for at in rng.uniform(0, L, 7):
        place(x, creak(rng.uniform(0.8, 1.8)) * 0.25, at)
    for at in rng.uniform(0, L, 4):
        place(x, twig() * 0.12, at)
    save('amb.forest', loopify(x), 0.5)


def amb_village():
    L = 32
    x = wind(L + 1, 0.8, 0.6)
    for at in rng.uniform(0, L, 4):
        place(x, creak(rng.uniform(1.5, 2.5), rng.uniform(60, 90)) * 0.35, at)   # ставни
    for at in rng.uniform(0, L, 2):
        clank = sum(np.sin(2 * np.pi * f * t_(1.5)) for f in (330, 527, 790)) * decay(1.5, 4)
        place(x, lp(clank, 1500) * 0.05, at)
    save('amb.village', loopify(x), 0.45)


def amb_mine():
    L = 30
    t = t_(L + 1)
    x = lp(noise(L + 1), 120) * 1.2 + np.sin(2 * np.pi * 38 * t) * 0.08
    for at in rng.uniform(0, L, 26):
        place(x, reverb(drip(), 1.2, 0.5) * rng.uniform(0.05, 0.18), at)
    for at in rng.uniform(0, L, 3):
        place(x, lp(twig(), 1500) * 0.4, at)
    save('amb.mine', loopify(x), 0.5)


def amb_station():
    L = 20
    t = t_(L + 1)
    hum = sum(np.sin(2 * np.pi * 50 * k * t) / k for k in (1, 2, 3, 5)) * 0.15
    x = wind(L + 1, 0.5, 0.4) + hum * (1 + 0.1 * np.sin(2 * np.pi * 0.3 * t))
    for at in rng.uniform(0, L, 10):
        place(x, click(0.02) * 0.3, at)
    save('amb.station', loopify(x), 0.45)


def amb_bunker():
    L = 24
    t = t_(L + 1)
    x = np.sin(2 * np.pi * 41 * t) * 0.4 + np.sin(2 * np.pi * 61.5 * t) * 0.25
    x += lp(choir(L + 1, [98, 104, 147], 'u'), 700) * 0.15 * (0.6 + 0.4 * np.sin(2 * np.pi * t / 12))
    for at in rng.uniform(0, L, 5):
        ping = sum(np.sin(2 * np.pi * f * t_(3)) for f in (211, 333, 571)) * decay(3, 1.5)
        place(x, ping * 0.04, at)
    save('amb.bunker', loopify(reverb(x, 2.5, 0.4)[:len(x)]), 0.55)


def amb_dread():
    L = 20
    t = t_(L + 1)
    x = (np.sin(2 * np.pi * 110 * t) + np.sin(2 * np.pi * 116.5 * t) + 0.6 * np.sin(2 * np.pi * 155.6 * t)) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.25 * t))
    x += lp(noise(L + 1), 400) * 0.3
    sw = noise(4) * np.linspace(0, 1, int(4 * SR)) ** 3
    for at in (3, 13):
        place(x, bp(sw, 400, 3000) * 0.3, at)
    save('amb.dread', loopify(x), 0.4)


def amb_relief():
    L = 30
    t = t_(L + 1)
    x = sum(np.sin(2 * np.pi * f * t) * g for f, g in ((220, 0.4), (277.2, 0.3), (329.6, 0.3), (440, 0.15))) * (0.7 + 0.3 * np.sin(2 * np.pi * t / 15))
    for at in rng.uniform(0, L, 9):   # синица: «цзинь-цзинь»
        tt = t_(0.12)
        chirp = np.sin(2 * np.pi * (4200 + 900 * tt / 0.12) * tt) * env(len(tt), 0.01, 0.05)
        place(x, chirp * 0.1, at)
        place(x, chirp * 0.1, at + 0.2)
    x += wind(L + 1, 0.15, 0.2)
    save('amb.relief', loopify(x), 0.4)


def amb_chase():
    bpm, bars = 132, 8
    beat = 60 / bpm
    L = beat * 4 * bars
    x = np.zeros(int(L * SR))
    for i in range(bars * 4):
        place(x, thump(48, 0.4, 9) * 1.0, i * beat)
        if i % 2 == 1:
            place(x, hp(noise(0.08), 3000) * decay(0.08, 50) * 0.15, i * beat)
    t = t_(L)
    ost = np.sign(np.sin(2 * np.pi * np.where((t / beat * 2).astype(int) % 4 < 2, 466, 494) * t)) * 0.08
    x += bp(ost, 300, 3000) + np.sin(2 * np.pi * 55 * t) * 0.15
    x += bp(noise(L), 2000, 6000) * 0.02 * (1 + np.sin(2 * np.pi * t / L * 4))
    save('amb.chase', x, 0.6)


# ---------------- короткие звуки ----------------
def one_shots():
    save('heartbeat', np.concatenate([thump(55, 0.22, 18), np.zeros(int(0.08 * SR)), thump(48, 0.3, 14) * 0.8]), 0.9)
    t = t_(2.6)
    br = bp(noise(2.6), 400, 2500) * (np.sin(np.pi * np.clip(t / 1.1, 0, 1)) ** 2 * (t < 1.1) + 0.8 * np.sin(np.pi * np.clip((t - 1.3) / 1.2, 0, 1)) ** 2 * (t > 1.3))
    save('breath', br * (1 + 0.3 * np.sin(2 * np.pi * 9 * t)), 0.6)
    k = np.zeros(int(1.3 * SR))
    for i, at in enumerate((0, 0.32, 0.64)):
        place(k, knock1() * (1 - i * 0.05), at, wrap=False)
    save('knock', reverb(k, 0.6, 0.25), 0.95)
    s = np.zeros(int(2.4 * SR))
    for i in range(4):
        place(s, lp(twig()[:4851] + step_forest()[:4851] * 0.6, 1800), i * 0.55 + rng.uniform(0, 0.05), wrap=False)
    save('steps_far', reverb(s, 1.2, 0.3), 0.7)
    save('steps_near', step_forest(), 0.8)
    wh = np.zeros(int(1.6 * SR))
    for at in (0, 0.5, 0.9):
        seg = bp(noise(0.45), 2000, 7000) * env(int(0.45 * SR), 0.05, 0.25)
        place(wh, seg, at, wrap=False)
    save('whisper', reverb(wh, 0.8, 0.3), 0.5)
    t = t_(3)
    st = bp(noise(3), 300, 5000) * 0.5 + (rng.random(len(t)) < 0.003) * rng.standard_normal(len(t)) * 2
    st += np.sin(2 * np.pi * (900 + 600 * np.sin(2 * np.pi * 0.4 * t)) * t) * 0.08
    save('radio_static', st * env(len(t), 0.05, 0.4), 0.7)
    save('radio_click', np.concatenate([click(0.02), np.zeros(800), click(0.015) * 0.6]), 0.8)
    # скример: резкий кластер + шум + визг
    t = t_(2.5)
    cl = sum(np.sign(np.sin(2 * np.pi * f * t)) for f in (233, 247, 262, 466, 494, 740)) / 6
    scr = np.sin(2 * np.pi * (1800 + 400 * np.sin(2 * np.pi * 13 * t)) * t) * 0.4
    hit = (bp(cl, 150, 5000) + scr + bp(noise(2.5), 500, 8000) * 0.6) * np.exp(-t * 2.2) + thump(40, 2.5, 3) * 0.8
    save('stinger', reverb(hit, 1.5, 0.3), 0.98)
    soft = thump(45, 2.0, 2.5) + bp(noise(2.0), 300, 1500) * np.linspace(1, 0, int(2.0 * SR)) ** 2 * 0.3
    save('stinger_soft', reverb(soft, 1.5, 0.3), 0.8)
    rv = (bp(noise(1.4), 800, 6000) * np.linspace(0, 1, int(1.4 * SR)) ** 3)
    save('pole_appear', np.concatenate([rv, thump(38, 0.8, 6)]), 0.85)
    save('pole_vanish', reverb(rv[::-1] * 0.7, 1.0, 0.4), 0.7)
    ps = creak(0.5, 70) * 0.6
    ps[:int(0.3 * SR)] += thump(60, 0.3, 18)
    save('pole_step', ps, 0.7)
    cl = np.zeros(int(0.9 * SR))
    for i in range(rng.integers(7, 11)):
        place(cl, bp(noise(0.01), 2500, 7000) * decay(0.01, 400) * rng.uniform(0.5, 1), i * rng.uniform(0.06, 0.1), wrap=False)
    save('listener_click', cl, 0.8)
    t = t_(1.2)
    f = 950 + 300 * np.sin(2 * np.pi * 9 * t) + 500 * t
    scream = np.sin(2 * np.pi * np.cumsum(f) / SR + 3 * np.sin(2 * np.pi * 330 * t))
    save('listener_scream', reverb(np.tanh(scream * 2) * env(len(t), 0.03, 0.4), 1.0, 0.3), 0.8)
    t = t_(0.4)
    save('listener_hurt', np.tanh(np.sin(2 * np.pi * np.cumsum(1400 - 800 * t) / SR) * 3) * env(len(t), 0.01, 0.2), 0.7)
    roar = choir(3.2, [55, 61, 73, 82, 110, 123], 'a')
    roar = np.tanh(roar * 1.5) * env(len(roar), 0.4, 1.2) + thump(35, 3.2, 1.5) * 0.4
    save('choir_roar', reverb(roar, 2.2, 0.35), 0.95)
    hum = choir(4.5, [98, 110, 131, 147, 196, 220], 'u') * env(int(4.5 * SR), 1.0, 1.5)
    save('choir_hum', reverb(hum, 2.0, 0.4), 0.8)
    t = t_(1.0)
    hh = choir(1.0, [400, 452, 523, 640], 'e') * env(len(t), 0.02, 0.6)
    save('choir_hurt', reverb(np.tanh(hh * 3), 1.0, 0.3), 0.85)
    save('flashlight_click', np.concatenate([click(0.015), np.zeros(600), click(0.01) * 0.5]), 0.6)
    t = t_(0.5)
    save('battery_dead', np.sin(2 * np.pi * np.cumsum(880 - 700 * t) / SR) * env(len(t), 0.01, 0.2) * 0.5, 0.5)
    t = t_(0.6)
    fz = thump(120, 0.25, 30)
    sp = hp(noise(0.6), 2000) * (rng.random(len(t)) < 0.02) * 3
    save('fuse', np.pad(fz, (0, len(t) - len(fz))) + sp * decay(0.6, 6), 0.8)
    t = t_(3.5)
    rate = 4 + 18 * np.clip(t / 2.5, 0, 1)
    pulse = (np.sin(2 * np.pi * np.cumsum(rate) / SR) > 0.7).astype(float)
    eng = lp(pulse, 600) * 2 + np.sin(2 * np.pi * np.cumsum(30 + 40 * np.clip(t / 2.5, 0, 1)) / SR) * 0.3
    save('generator', eng * env(len(t), 0.05, 0.5), 0.8)
    t = t_(2.8)
    swell = sum(np.sin(2 * np.pi * 50 * k * t) / k for k in (1, 2, 3, 4)) * np.clip(t / 1.2, 0, 1) * env(len(t), 0.01, 0.8)
    save('power_on', np.pad(thump(70, 0.4, 12), (0, len(t) - int(0.4 * SR))) * 1.5 + swell * 0.6, 0.85)
    t = t_(1.5)
    buzz = np.sign(np.sin(2 * np.pi * 100 * t)) * 0.3 * (lp(rng.random(len(t)), 25, 1) > 0.48)
    save('lamp_buzz', bp(buzz, 100, 4000), 0.5)
    save('door_creak', creak(1.4, 120), 0.7)
    t = t_(4.5)
    lift = lp(noise(4.5), 200) * 2 + np.sin(2 * np.pi * 45 * t) * 0.3
    ch = np.zeros(len(t))
    for at in np.arange(0, 4.3, 0.18):
        place(ch, bp(noise(0.03), 2000, 6000) * decay(0.03, 120) * 0.4, at, wrap=False)
    save('lift', (lift + ch) * env(len(t), 0.4, 0.6), 0.8)
    save('tape_click', np.concatenate([click(0.02), np.zeros(1500), thump(200, 0.08, 60) * 0.5, hp(noise(0.5), 3000) * 0.05]), 0.7)
    boom = thump(36, 4.0, 1.4) + lp(noise(4.0), 200) * decay(4.0, 2) * 0.6
    save('title_hit', reverb(boom, 3.0, 0.45), 0.9)
    t = t_(0.15)
    save('pebble', (np.sin(2 * np.pi * 2300 * t) * 0.5 + bp(noise(0.15), 1500, 6000)) * decay(0.15, 40), 0.7)
    save('branch_snap', reverb(np.tanh(bp(noise(0.12), 600, 5000) * 4) * decay(0.12, 35), 0.9, 0.3), 0.85)
    t = t_(2.2)
    ds = np.sin(2 * np.pi * np.cumsum(700 + 250 * np.sin(2 * np.pi * 5 * t) - 200 * t) / SR) * env(len(t), 0.2, 0.9)
    save('distant_scream', reverb(lp(np.tanh(ds * 2), 1400), 2.5, 0.6), 0.6)
    save('wind_gust', wind(4, 1.2, 0.8) * env(int(4 * SR), 1.5, 2.0), 0.7)


if __name__ == '__main__':
    amb_forest(); amb_village(); amb_mine(); amb_station(); amb_bunker(); amb_dread(); amb_relief(); amb_chase()
    one_shots()
