#!/usr/bin/env python3
"""Мягкие звуки интерфейса LANTERN (свои, сгенерированные): tap, success, join, error.
WAV 22050 Гц, моно, 16 бит — по несколько килобайт. python3 tools/make_sounds.py"""
import math, os, struct, wave

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'assets', 'sfx')


def tone(freqs, dur, vol=0.35, attack=0.004, decay=6.0, glide=0.0):
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * math.exp(-decay * t / dur)
        v = 0.0
        for k, (f, a) in enumerate(freqs):
            ff = f * (1.0 + glide * t / dur)
            v += a * math.sin(2 * math.pi * ff * t) + 0.25 * a * math.sin(2 * math.pi * ff * 2 * t)
        out.append(v * env * vol)
    return out


def seq(parts):
    out = []
    for start, samples in parts:
        i0 = int(start * RATE)
        if len(out) < i0 + len(samples):
            out.extend([0.0] * (i0 + len(samples) - len(out)))
        for i, s in enumerate(samples):
            out[i0 + i] += s
    return out


def save(name, samples):
    os.makedirs(OUT, exist_ok=True)
    fade = int(RATE * 0.01)
    for i in range(min(fade, len(samples))):
        samples[-1 - i] *= i / fade
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1.0, min(1.0, s)) * 32000)) for s in samples))
    print(name, len(samples) * 2 + 44, 'байт')


save('tap', tone([(880, 1.0)], 0.05, vol=0.22, decay=7.0))
save('success', seq([(0.0, tone([(659.3, 1.0)], 0.12, vol=0.28)), (0.09, tone([(987.8, 1.0)], 0.2, vol=0.28))]))
save('join', seq([(0.0, tone([(523.3, 1.0)], 0.12, vol=0.26)), (0.08, tone([(659.3, 1.0)], 0.12, vol=0.26)), (0.16, tone([(784.0, 1.0)], 0.24, vol=0.28))]))
save('error', seq([(0.0, tone([(311.1, 1.0)], 0.12, vol=0.3)), (0.1, tone([(233.1, 1.0)], 0.2, vol=0.3))]))
