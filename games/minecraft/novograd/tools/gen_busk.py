#!/usr/bin/env python3
"""Уличные музыканты Новограда: короткие петли ~16 с (гитара под перебор, саксофон, бит для брейкданса).
Синтез — теми же инструментами, что и радио (tools/gen_radio.py). Выход: assets/novograd/sounds/busk/*.ogg
Запуск из папки novograd: python3 tools/gen_busk.py"""
import os, sys
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_radio as R  # noqa: E402

R.OUT = os.path.join(R.HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'sounds', 'busk')
os.makedirs(R.OUT, exist_ok=True)
SR = R.SR
rng = np.random.default_rng(31)


def acoustic(f, dur):
    """Акустическая струна: Карплус — Стронг (шумовой импульс в линии задержки)."""
    n = int(dur * SR)
    p = max(2, int(SR / f))
    buf = rng.uniform(-1, 1, p)
    out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % p]
        buf[i % p] = 0.5 * (buf[i % p] + buf[(i + 1) % p]) * 0.996
    return R.lowpass(out, 3500) * R.env(n, 0.002, 0.05, 0.9, 0.05)


def guitar(name='guitar'):
    """Перебор под четыре аккорда (Am — F — C — G), темп 92, 6 тактов ≈ 15.6 с."""
    bpm = 92; beat = 60 / bpm; bars = 6
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[45, 52, 57, 60, 64], [41, 48, 53, 57, 60], [48, 52, 55, 60, 64], [43, 47, 50, 55, 59]]
    pattern = [0, 2, 3, 4, 1, 3, 2, 4]  # бас — струны вверх-вниз
    for bar in range(bars):
        ch = prog[bar % 4] if bar < 4 else prog[(bar - 4) % 2]
        t0 = bar * 4 * beat
        for e, s in enumerate(pattern):
            place_at = t0 + e * beat / 2
            R.place(buf, acoustic(R.hz(ch[s]), beat * 1.6), place_at, 0.5 if s == 0 else 0.32)
        if bar % 2 == 1:  # «бой» в конце такта
            for i, n in enumerate(ch[1:]):
                R.place(buf, acoustic(R.hz(n), beat), t0 + 3.5 * beat + i * 0.012, 0.25)
    R.master(buf[:int(L * SR)], name, 0.8)


def sax(name='sax'):
    """Саксофон на улице: медленная блюзовая мелодия с вибрато поверх мягкого баса, 8 тактов по 2 с."""
    bpm = 120; beat = 60 / bpm; bars = 8
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    melody = [(62, 1.5), (65, 0.5), (67, 1), (69, 1), (67, 2), (65, 1), (62, 1),
              (60, 1.5), (62, 0.5), (65, 2), (62, 2), (67, 1), (69, 1), (70, 1.5), (69, 0.5), (67, 2),
              (65, 1), (62, 1), (60, 2), (62, 4)]
    t = 0.0
    for n, d in melody:
        tt = R.t_(d * beat * 0.97)
        f = R.hz(n) * (1 + 0.009 * np.sin(2 * np.pi * 5.2 * tt) * np.minimum(1, tt * 2))
        ph = 2 * np.pi * np.cumsum(f) / SR
        tone = np.tanh(np.sin(ph) * 2.2 + 0.45 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph))
        breath = rng.normal(0, 0.04, len(tt))
        R.place(buf, R.lowpass(tone + breath, 2600) * R.env(len(tt), 0.06, 0.15, 0.85, 0.12), t, 0.5)
        t += d * beat
    for bar in range(bars):
        root = [38, 43, 38, 45, 43, 38, 45, 38][bar]
        for b in range(4):
            tt = R.t_(beat * 0.9)
            R.place(buf, np.sin(2 * np.pi * R.hz(root + (0, 7, 12, 7)[b]) * tt) * np.exp(-tt * 3), bar * 4 * beat + b * beat, 0.22)
    R.master(buf[:int(L * SR)], name, 0.8)


def beat(name='beat'):
    """Брейк-бит из бумбокса: 95 BPM, скретчи и бас, 6 тактов ≈ 15 с."""
    bpm = 95; b = 60 / bpm; bars = 6
    L = bars * 4 * b
    buf = np.zeros(int(L * SR) + SR)
    for bar in range(bars):
        t0 = bar * 4 * b
        for k in (0, 1.75, 2.5):
            R.place(buf, R.kick(0.35, 1.2), t0 + k * b, 0.9)
        for k in (1, 3):
            R.place(buf, R.mix(R.snare(0.25, 210), R.clap()), t0 + k * b, 0.7)
        for e in range(8):
            R.place(buf, R.hat(0.05, e % 4 == 3), t0 + e * b / 2, 0.25)
        root = [36, 36, 39, 34][bar % 4]
        R.place(buf, R.bass808(R.hz(root), b * 1.5, R.hz(root + 12)), t0, 0.6)
        R.place(buf, R.bass808(R.hz(root + 7), b, None), t0 + 2.5 * b, 0.45)
        if bar % 2 == 1:  # скретч
            tt = R.t_(0.3)
            f = 400 + 700 * np.abs(np.sin(2 * np.pi * 7 * tt))
            ph = 2 * np.pi * np.cumsum(f) / SR
            R.place(buf, R.lowpass(np.sign(np.sin(ph)) * rng.uniform(0.6, 1, len(tt)), 3000) * R.env(len(tt), 0.01, 0.05, 0.8, 0.05), t0 + 3.5 * b, 0.22)
    R.master(buf[:int(L * SR)], name, 0.85)


if __name__ == '__main__':
    guitar()
    sax()
    beat()
